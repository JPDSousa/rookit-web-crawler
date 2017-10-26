package org.rookit.crawler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

import de.umass.lastfm.Caller;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.MusicEntry;
import de.umass.lastfm.PaginatedResult;
import de.umass.lastfm.User;

class LastFM implements MusicService {

	private final String apiKey;
	private final String user;

	private final ArtistFactory artistFactory;
	private final AlbumFactory albumFactory;
	private final Parser<String, SingleTrackAlbumBuilder> parser;

	LastFM(LastFMConfig config) {
		this.apiKey = config.getApiKey();
		this.user = config.getUser();
		final Caller caller = Caller.getInstance();
		caller.setUserAgent("tst");
		this.artistFactory = ArtistFactory.getDefault();
		this.albumFactory = AlbumFactory.getDefault();
		parser = createParser();
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
		final String trackTitle = track.getTitle().toString();
		return StreamSupport.stream(track.getMainArtists().spliterator(), true)
				.map(Artist::getName)
				.map(name -> de.umass.lastfm.Track.search(name, trackTitle, 200, apiKey))
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
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

	private SingleTrackAlbumBuilder parseTrackTitle(de.umass.lastfm.Track source) {
		final SingleTrackAlbumBuilder result = parser.parse(source.getName());
		return result.getScore() > 0 ? result : SingleTrackAlbumBuilder.create().withTitle(source.getName());
	}

	private byte[] getBiggest(MusicEntry source) {
		final ImageSize biggest = source.availableSizes().parallelStream()
				.reduce(this::compare)
				.get();
		try {
			if(biggest != null) {
				final URL url = new URL(source.getImageURL(biggest));
				return IOUtils.toByteArray(url.openStream());
			}
			return null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ImageSize compare(ImageSize left, ImageSize right) {
		return left.compareTo(right) > 0 ? left : right;
	}

	@Override
	public Stream<Track> searchArtistTracks(Artist artist) {
		final Stream<Track> topTracks = de.umass.lastfm.Artist.getTopTracks(artist.getName(), apiKey)
				.parallelStream()
				.map(this::toTrack);
		final Stream<Track> allTracks = de.umass.lastfm.Artist.search(artist.getName(), apiKey)
				.parallelStream()
				.map(de.umass.lastfm.Artist::getName)
				.flatMap(this::searchArtistTracks)
				.map(this::toTrack);
		return Stream.concat(topTracks, allTracks);
	}

	private Stream<de.umass.lastfm.Track> searchArtistTracks(String artistName) {
		final PaginatedResult<de.umass.lastfm.Track> firstResults = User.getArtistTracks(user, artistName, apiKey);
		final Stream<de.umass.lastfm.Track> otherResults = IntStream.range(1, firstResults.getTotalPages())
				.mapToObj(i -> User.getArtistTracks(user, artistName, i, 0, 0, apiKey))
				.map(PaginatedResult::getPageResults)
				.flatMap(Collection::parallelStream);
		return Stream.concat(firstResults.getPageResults().parallelStream(), otherResults);
	}

	@Override
	public Stream<Artist> searchArtist(Artist artist) {
		return de.umass.lastfm.Artist.search(artist.getName(), apiKey).parallelStream()
				.map(artistRes -> toArtist(artistRes, artist.getName()));
	}

	private Artist toArtist(de.umass.lastfm.Artist source, String originalName) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final List<String> names = artists.parallelStream()
				.map(Artist::getName)
				.collect(Collectors.toList());
		final String name = bestMatch(names, originalName);
		return artistFactory.createArtist(TypeArtist.GROUP, name);
	}

	private String bestMatch(List<String> search, String str) {
		if(search.isEmpty()) {
			return null;
		}
		if(search.size() == 1) {
			return search.get(0);
		}
		final LevenshteinDistance calculator = LevenshteinDistance.getDefaultInstance();
		return StreamSupport.stream(search.spliterator(), true)
				.map(searchStr -> Pair.of(searchStr, calculator.apply(searchStr, str)))
				.reduce(this::compare)
				.map(Pair::getLeft)
				.orElse(null);
	}

	private Pair<String, Integer> compare(Pair<String, Integer> left, Pair<String, Integer> right) {
		final int leftScore = left.getRight();
		final int rigthScore = right.getRight();
		return leftScore > rigthScore ? left : right;
	}

	@Override
	public Stream<Album> searchAlbum(Album album) {
		return de.umass.lastfm.Album.search(album.getTitle(), apiKey)
				.parallelStream()
				.map(this::toAlbum);
	}

	private Album toAlbum(de.umass.lastfm.Album source) {
		final Pair<TypeRelease, String> albumMeta = TypeRelease.parseAlbumName(source.getName(), TypeRelease.STUDIO);
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getArtist());
		final Album album = albumFactory.createSingleArtistAlbum(albumMeta.getRight(), albumMeta.getLeft(), artists);
		album.setCover(getBiggest(source));
		final LocalDate releaseDate = source.getReleaseDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		album.setReleaseDate(releaseDate);
		source.getTracks().forEach(track -> album.addTrack(toTrack(track), track.getPosition()));
		//TODO add this fields
		source.getId();
		source.getMbid();
		source.getPlaycount();
		return album;
	}

	@Override
	public Stream<Genre> searchGenre(Genre genre) {
		return Stream.empty();
	}

	@Override
	public Stream<Track> searchRelatedTracks(Track track) {
		return StreamSupport.stream(track.getMainArtists().spliterator(), true)
				.map(Artist::getName)
				.map(artistName -> de.umass.lastfm.Track.getSimilar(artistName, track.getTitle().toString(), apiKey))
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
	}

	@Override
	public Stream<Artist> searchRelatedArtists(Artist artist) {
		return de.umass.lastfm.Artist.getSimilar(artist.getName(), apiKey)
				.parallelStream()
				.map(this::toArtist);
	}

	private Artist toArtist(de.umass.lastfm.Artist source) {
		final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, source.getName());
		artist.setPicture(getBiggest(source));
		artist.setPlays(source.getPlaycount());
		// TODO add these fields
		source.getId();
		source.getListeners();
		source.getMbid();
		source.getSimilarityMatch();
		return artist;
	}

	@Override
	public Stream<Genre> searchRelatedGenres(Genre genre) {
		return Stream.empty();
	}
}
