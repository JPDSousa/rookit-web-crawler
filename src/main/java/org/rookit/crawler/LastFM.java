package org.rookit.crawler;

import static org.rookit.crawler.AvailableServices.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.rookit.crawler.config.LastFMConfig;
import org.rookit.crawler.utils.CrawlerIOUtils;
import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;
import org.rookit.parser.result.SingleTrackAlbumBuilder;

import com.google.common.collect.Maps;

import de.umass.lastfm.Caller;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.MusicEntry;
import de.umass.lastfm.PaginatedResult;
import de.umass.lastfm.User;

class LastFM extends AbstractMusicService {

	private final String apiKey;
	private final String user;

	LastFM(LastFMConfig config) {
		super(5, config.getLevenshteinThreshold());
		this.apiKey = config.getApiKey();
		this.user = config.getUser();
		final Caller caller = Caller.getInstance();
		caller.setUserAgent("tst");
		caller.getLogger().setUseParentHandlers(config.isDebug());
	}
	
	private <T> T schedule(Callable<T> executor) {
		limiter.acquire();
		try {
			return executor.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Stream<Track> searchTrack(Track track) {
		final String trackTitle = track.getTitle().toString();
		return StreamSupport.stream(track.getMainArtists().spliterator(), true)
				.map(Artist::getName)
				.map(name -> schedule(() -> de.umass.lastfm.Track.search(name, trackTitle, 200, apiKey)))
				.flatMap(Collection::parallelStream)
				.map(this::toTrack)
				.filter(Objects::nonNull);
	}

	private Track toTrack(de.umass.lastfm.Track source) {
		final Set<Artist> artists = toArtists(source);
		final Document mBrainz = new Document(ID, source.getMbid());
		final Document lastFM = new Document(ID, source.getId())
				.append(LISTENERS, source.getListeners())
				.append(LOCATION, source.getLocation())
				.append(URL, source.getUrl())
				.append(TAGS, source.getTags())
				.append(WIKI, source.getWikiSummary())
				.append(PLAYS, source.getPlaycount());
		final SingleTrackAlbumBuilder builder = parseTrackTitle(source)
				.withAlbum(toAlbum(source, artists))
				.withMainArtists(artists)
				.withExternalMetadata(MBRAINZ.name(), mBrainz)
				.withExternalMetadata(LASTFM.name(), lastFM)
				.withDuration(Duration.ofSeconds(source.getDuration()));
		final Track track = builder.getTrack();
		final long plays = source.getPlaycount();
		if(plays > 0) {
			track.setPlays(plays);
		}
		return track;
	}

	private Set<Artist> toArtists(de.umass.lastfm.Track source) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getArtist());
		setMBid(source.getArtistMbid(), artists);
		return artists;
	}

	private void setMBid(String id, final Set<Artist> artists) {
		if(artists.size() == 1) {
			final Document mBrainz = new Document(ID, id);
			//get single artist
			for(Artist artist : artists) {
				artist.putExternalMetadata(MBRAINZ.name(), mBrainz);
			}
		}
	}
	
	private Album toAlbum(de.umass.lastfm.Track track, Set<Artist> artists) {
		final Album album = albumFactory.createSingleArtistAlbum(track.getAlbum(), artists);
		final Document mBrainz = new Document(ID, track.getAlbumMbid());
		album.setCover(getBiggest(track));
		album.putExternalMetadata(MBRAINZ.name(), mBrainz);
		
		return album;
	}

	private SingleTrackAlbumBuilder parseTrackTitle(de.umass.lastfm.Track source) {
		final SingleTrackAlbumBuilder result = parser.parse(source.getName());
		// TODO set score in else clause
		return result.getScore() > 0 ? result : SingleTrackAlbumBuilder.create().withTitle(source.getName());
	}

	private byte[] getBiggest(MusicEntry source) {
		final ImageSize biggest = source.availableSizes().parallelStream()
				.reduce((left, right) -> compare(left, right, source))
				.get();
		if(biggest != null && !source.getImageURL(biggest).isEmpty()) {
			return CrawlerIOUtils.downloadImage(source.getImageURL(biggest));
		}
		return null;
	}

	private ImageSize compare(ImageSize left, ImageSize right, MusicEntry source) {
		final String urlLeft = source.getImageURL(left);
		final String urlRight = source.getImageURL(right);
		if(urlLeft == null || urlLeft.isEmpty()) {
			return right;
		}
		if(urlRight == null || urlRight.isEmpty()) {
			return left;
		}
		return left.compareTo(right) > 0 ? left : right;
	}

	@Override
	public Stream<Track> searchArtistTracks(Artist artist) {
		final Stream<Track> topTracks = schedule(() -> de.umass.lastfm.Artist.getTopTracks(artist.getName(), apiKey))
				.parallelStream()
				.map(this::toTrack);
		final Stream<Track> allTracks = schedule(() -> de.umass.lastfm.Artist.search(artist.getName(), apiKey))
				.parallelStream()
				.map(de.umass.lastfm.Artist::getName)
				.flatMap(this::searchArtistTracks)
				.map(this::toTrack);
		return Stream.concat(topTracks, allTracks)
				.filter(Objects::nonNull);
	}

	private Stream<de.umass.lastfm.Track> searchArtistTracks(String artistName) {
		final PaginatedResult<de.umass.lastfm.Track> firstResults = schedule(() -> User.getArtistTracks(user, artistName, apiKey));
		final Stream<de.umass.lastfm.Track> otherResults = IntStream.range(1, firstResults.getTotalPages())
				.mapToObj(i -> schedule(() -> User.getArtistTracks(user, artistName, i, 0, 0, apiKey)))
				.map(PaginatedResult::getPageResults)
				.flatMap(Collection::parallelStream);
		return Stream.concat(firstResults.getPageResults().parallelStream(), otherResults)
				.filter(Objects::nonNull);
	}

	@Override
	public Stream<Artist> searchArtist(Artist artist) {
		return schedule(() -> de.umass.lastfm.Artist.search(artist.getName(), apiKey)).parallelStream()
				.map(artistRes -> toArtist(artistRes, artist.getName()))
				.filter(Objects::nonNull);
	}

	private Artist toArtist(de.umass.lastfm.Artist source, String originalName) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final Artist artist = bestMatchArtist(artists, originalName);
		if(artist != null) {
			final byte[] cover = getBiggest(source);
			final Document lastFM = new Document(ID, source.getId())
					.append(LISTENERS, source.getListeners())
					.append(TAGS, source.getTags())
					.append(URL, source.getUrl())
					.append(WIKI, source.getWikiSummary())
					.append(PLAYS, source.getPlaycount());
			artist.putExternalMetadata(LASTFM.name(), lastFM);
			// only sets the mbid if artists = artist
			setMBid(source.getMbid(), artists);
			if(cover != null) {
				artist.setPicture(cover);
			}
			return artist;
		}
		return null;
	}
	
	private Artist bestMatchArtist(Collection<Artist> artists, String originalName) {
		final Map<String, Artist> names = artists.stream()
				.collect(Collectors.groupingBy(
						Artist::getName, 
						Maps::newLinkedHashMap, 
						Collectors.collectingAndThen(
								Collectors.reducing((left, right) -> left), 
								Optional::get)));
		
		final String name = bestMatch(names.keySet(), originalName);
		if(name != null) {
			return names.get(name);
		}
		return null;
	}

	@Override
	public Stream<Album> searchAlbum(Album album) {
		return schedule(() -> de.umass.lastfm.Album.search(album.getTitle(), apiKey))
				.parallelStream()
				.map(this::toAlbum)
				.filter(Objects::nonNull);
	}

	private Album toAlbum(de.umass.lastfm.Album source) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getArtist());
		final Album album = albumFactory.createSingleArtistAlbum(source.getName(), artists);
		final Document mBrainz = new Document(ID, source.getMbid());
		final Document lastFM = new Document(ID, source.getId())
				.append(PLAYS, source.getPlaycount())
				.append(LISTENERS, source.getListeners())
				.append(TAGS, source.getTags())
				.append(URL, source.getUrl())
				.append(WIKI, source.getWikiSummary());
		album.putExternalMetadata(MBRAINZ.name(), mBrainz);
		album.putExternalMetadata(LASTFM.name(), lastFM);
		final byte[] cover = getBiggest(source);
		if(cover != null) {
			album.setCover(cover);
		}
		final LocalDate releaseDate = getReleaseDate(source);
		if(releaseDate != null) {
			album.setReleaseDate(releaseDate);
		}
		final Collection<de.umass.lastfm.Track> tracks = source.getTracks();
		if(tracks != null) {
			tracks.forEach(track -> album.addTrack(toTrack(track), track.getPosition()));
		}
		//TODO add these fields
		source.getId();
		source.getMbid();
		source.getPlaycount();
		source.getListeners();
		source.getTags();
		source.getUrl();
		source.getWikiSummary();
		return album;
	}

	private LocalDate getReleaseDate(de.umass.lastfm.Album source) {
		final Date releaseDate = source.getReleaseDate();
		return releaseDate != null ? releaseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
	}

	@Override
	public Stream<Genre> searchGenre(Genre genre) {
		return Stream.empty();
	}

	@Override
	public Stream<Track> searchRelatedTracks(Track track) {
		return StreamSupport.stream(track.getMainArtists().spliterator(), true)
				.map(Artist::getName)
				.map(artistName -> schedule(() -> de.umass.lastfm.Track.getSimilar(artistName, track.getTitle().toString(), apiKey)))
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
	}

	@Override
	public Stream<Artist> searchRelatedArtists(Artist artist) {
		return de.umass.lastfm.Artist.getSimilar(artist.getName(), apiKey)
				.parallelStream()
				.map(a -> toArtist(a, artist.getName()));
	}

	@Override
	public Stream<Genre> searchRelatedGenres(Genre genre) {
		return Stream.empty();
	}
}
