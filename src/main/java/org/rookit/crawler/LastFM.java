package org.rookit.crawler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.rookit.crawler.config.LastFMConfig;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.AlbumFactory;
import org.rookit.dm.album.TypeRelease;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.ArtistFactory;
import org.rookit.dm.artist.TypeArtist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;
import org.rookit.parser.config.ParserConfiguration;
import org.rookit.parser.config.ParsingConfig;
import org.rookit.parser.formatlist.FormatList;
import org.rookit.parser.parser.Field;
import org.rookit.parser.parser.Parser;
import org.rookit.parser.parser.ParserFactory;
import org.rookit.parser.result.SingleTrackAlbumBuilder;

import com.google.common.util.concurrent.RateLimiter;

import de.umass.lastfm.Caller;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.MusicEntry;
import de.umass.lastfm.PaginatedResult;
import de.umass.lastfm.User;

class LastFM implements MusicService {

	private final String apiKey;
	private final String user;
	private final LastFMConfig config;

	private final ArtistFactory artistFactory;
	private final AlbumFactory albumFactory;
	private final Parser<String, SingleTrackAlbumBuilder> parser;
	private final LevenshteinDistance distance;
	private final RateLimiter limiter;

	LastFM(LastFMConfig config) {
		limiter = RateLimiter.create(5);
		this.config = config;
		this.apiKey = config.getApiKey();
		this.user = config.getUser();
		distance = LevenshteinDistance.getDefaultInstance();
		final Caller caller = Caller.getInstance();
		caller.setUserAgent("tst");
		caller.getLogger().setUseParentHandlers(config.isDebug());
		this.artistFactory = ArtistFactory.getDefault();
		this.albumFactory = AlbumFactory.getDefault();
		parser = createParser();
	}
	
	private <T> T schedule(Callable<T> executor) {
		limiter.acquire();
		try {
			return executor.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Parser<String, SingleTrackAlbumBuilder> createParser() {
		try {
			FormatList formats = FormatList.readFromPath(Paths.get("src", "main", "resources", "parser", "formats.txt"));
			final ParserFactory factory = ParserFactory.create();
			final ParsingConfig topConfig = new ParsingConfig();
			final ParserConfiguration config = ParserConfiguration.create(SingleTrackAlbumBuilder.class, topConfig);
			config.withRequiredFields(new Field[0]);
			config.withTrackFormats(formats.getAll().collect(Collectors.toList()));
			return factory.newFormatParser(config);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Stream<Track> searchTrack(Track track) {
		final Document mBrainz = track.getExternalMetadata(MBRAINZ.name());
		final Stream<de.umass.lastfm.Track> rawStream;
		if(mBrainz != null && mBrainz.getString(ID) != null) {
			final String id = mBrainz.getString(ID);
			rawStream = Stream.of(schedule(() -> de.umass.lastfm.Track.getInfo(null, id, apiKey)));
		}
		else {
			final String trackTitle = track.getTitle().toString();
			rawStream = StreamSupport.stream(track.getMainArtists().spliterator(), true)
					.map(Artist::getName)
					.map(name -> schedule(() -> de.umass.lastfm.Track.search(name, trackTitle, 200, apiKey)))
					.flatMap(Collection::parallelStream); 
		}
		return rawStream.map(this::toTrack)
				.filter(Objects::nonNull);
	}

	private Track toTrack(de.umass.lastfm.Track source) {
		final SingleTrackAlbumBuilder builder = parseTrackTitle(source)
				//.withCover(getBiggest(source))
				//.withAlbumTitle(source.getAlbum()) TODO parse album title
				.withMainArtists(artistFactory.getArtistsFromFormat(source.getArtist()))
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
	
	private Album toAlbum(de.umass.lastfm.Track track, Set<Artist> artists) {
		final Album album = albumFactory.createSingleArtistAlbum(track.getAlbum(), artists);
		final Document mBrainz = new Document(ID, track.getAlbumMbid());
		album.setCover(getBiggest(track));
		album.putExternalMetadata(MBRAINZ.name(), mBrainz);
		
		return album;
	}

	private SingleTrackAlbumBuilder parseTrackTitle(de.umass.lastfm.Track source) {
		final SingleTrackAlbumBuilder result = parser.parse(source.getName());
		return result.getScore() > 0 ? result : SingleTrackAlbumBuilder.create().withTitle(source.getName());
	}

	private byte[] getBiggest(MusicEntry source) {
		final ImageSize biggest = source.availableSizes().parallelStream()
				.reduce((left, right) -> compare(left, right, source))
				.get();
		try {
			if(biggest != null && !source.getImageURL(biggest).isEmpty()) {
				final URL url = new URL(source.getImageURL(biggest));
				return IOUtils.toByteArray(url.openStream());
			}
			return null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
		final List<String> names = artists.stream()
				.map(Artist::getName)
				.collect(Collectors.toList());
		final String name = bestMatch(names, originalName);
		if(name != null) {
			final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, name);
			final byte[] cover = getBiggest(source);
			final int plays = source.getPlaycount();
			if(cover != null) {
				artist.setPicture(cover);
			}
			if(plays > 0) {
				artist.setPlays(plays);
			}
			// TODO add these fields
			source.getId();
			source.getListeners();
			source.getMbid();
			source.getSimilarityMatch();
			return artist;
		}
		return null;
	}

	private String bestMatch(List<String> search, String str) {
		if(search.isEmpty()) {
			return null;
		}
		final String lowerString = str.toLowerCase();
		return search.parallelStream()
				.map(String::toLowerCase)
				.filter(searchStr -> !containsAny(searchStr, Artist.SUSPICIOUS_NAME_CHARSEQS))
				.map(searchStr -> Pair.of(searchStr, distance.apply(searchStr, lowerString)))
				.filter(pair -> pair.getRight() >= 0)
				.filter(pair -> pair.getRight() < config.getLevenshteinThreshold())
				.reduce(this::compare)
				.map(Pair::getLeft)
				.orElse(null);
	}

	private boolean containsAny(String searchStr, String[] strs) {
		return Arrays.stream(strs).anyMatch(str -> searchStr.contains(str));
	}

	private Pair<String, Integer> compare(Pair<String, Integer> left, Pair<String, Integer> right) {
		final int leftScore = left.getRight();
		final int rigthScore = right.getRight();
		return leftScore < rigthScore ? left : right;
	}

	@Override
	public Stream<Album> searchAlbum(Album album) {
		return schedule(() -> de.umass.lastfm.Album.search(album.getTitle(), apiKey))
				.parallelStream()
				.map(this::toAlbum)
				.filter(Objects::nonNull);
	}

	private Album toAlbum(de.umass.lastfm.Album source) {
		final Pair<TypeRelease, String> albumMeta = TypeRelease.parseAlbumName(source.getName(), TypeRelease.STUDIO);
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getArtist());
		final Album album = albumFactory.createSingleArtistAlbum(albumMeta.getRight(), albumMeta.getLeft(), artists);
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
