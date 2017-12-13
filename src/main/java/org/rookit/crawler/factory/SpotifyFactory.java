package org.rookit.crawler.factory;

import org.rookit.dm.track.Track;
import org.rookit.dm.track.audio.TrackKey;
import org.rookit.dm.track.audio.TrackMode;

import com.google.common.collect.Sets;
import com.wrapper.spotify.models.album.AlbumType;
import com.wrapper.spotify.models.album.ReleaseDatePrecision;
import com.wrapper.spotify.models.album.SimpleAlbum;
import com.wrapper.spotify.models.artist.SimpleArtist;
import com.wrapper.spotify.models.audio.AudioFeature;
import com.wrapper.spotify.models.image.Image;
import com.wrapper.spotify.models.track.SimpleTrack;

import static org.rookit.crawler.AvailableServices.SPOTIFY;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import static org.rookit.crawler.MusicService.*;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.rookit.crawler.utils.CrawlerIOUtils;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.TypeRelease;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;

@SuppressWarnings("javadoc")
public class SpotifyFactory extends AbstractModelFactory<com.wrapper.spotify.models.artist.Artist, com.wrapper.spotify.models.album.Album, com.wrapper.spotify.models.track.Track> {

	private static final float IS_ACOUSTIC_THRESHOLD = 0.8f;
	private static final float IS_INSTRUMENTAL_THRESHOLD = 0.6f;
	private static final float IS_LIVE_THRESHOLD = 0.8f;
	
	@Override
	public Track toTrack(com.wrapper.spotify.models.track.Track source) {
		final Track track = toTrack((SimpleTrack) source);
		track.getExternalMetadata(SPOTIFY.name()).append(POPULARITY, source.getPopularity());
		// TODO add these fields
		source.getExternalIds();
		return track;
	}
	
	public Track toTrack(com.wrapper.spotify.models.track.Track source, AudioFeature audioFeature) {
		final Track track = toTrack(source);
		final boolean isAcoustic = audioFeature.getAcousticness() >= IS_ACOUSTIC_THRESHOLD;
		final Duration duration = Duration.ofMillis(audioFeature.getDurationMs());
		final boolean isInstrumental = audioFeature.getInstrumentalness() >= IS_INSTRUMENTAL_THRESHOLD;
		final TrackKey trackKey = TrackKey.values()[audioFeature.getKey()];
		final boolean isLive = audioFeature.getLiveness() >= IS_LIVE_THRESHOLD;
		final TrackMode trackMode = TrackMode.values()[audioFeature.getMode()];
		final short bpm = (short) Math.round(audioFeature.getTempo());
		track.setAcoustic(isAcoustic);
		track.setDanceability(audioFeature.getDanceability());
		track.setDuration(duration);
		track.setEnergy(audioFeature.getEnergy());
		track.setInstrumental(isInstrumental);
		track.setTrackKey(trackKey);
		track.setLive(isLive);
		track.setTrackMode(trackMode);
		track.setBPM(bpm);
		track.setValence(audioFeature.getValence());
		return track;
	}

	private Track toTrack(SimpleTrack source) {
		// TODO add these fields
		source.getExternalUrls();
		final Document spotify = new Document(ID, source.getId())
				.append(MARKETS, source.getAvailableMarkets())
				.append(URL, source.getHref())
				.append(PREVIEW, source.getPreviewUrl())
				.append(URI, source.getUri());
		final Track track = parser.parse(source.getName())
				.orElseThrow(() -> new RuntimeException("Cannot parse" + source.getName()))
				.withDisc(source.getDiscNumber()+"")
				.withNumber(source.getTrackNumber())
				.withDuration(Duration.ofMillis(source.getDuration()))
				.withExplicit(source.isExplicit())
				.withExternalMetadata(SPOTIFY.name(), spotify)
				.getTrack();
		final Collection<Artist> features = track.getFeatures();
		final Set<Artist> artists = source.getArtists().parallelStream()
				.map(this::flatArtist)
				.flatMap(Collection::parallelStream)
				.peek(features::remove)
				.collect(Collectors.toSet());
		track.setFeatures(Sets.newLinkedHashSet(features));
		track.setMainArtists(artists);
		return track;
	}

	@Override
	public Album toAlbum(com.wrapper.spotify.models.album.Album source) {
		final Set<Artist> artists = source.getArtists().stream()
				.map(this::flatArtist)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		final Album album = toAlbum(source, artists);
		album.getExternalMetadata(SPOTIFY.name()).append(POPULARITY, source.getPopularity());
		album.setGenres(source.getGenres().stream()
				.map(genreFactory::createGenre)
				.collect(Collectors.toSet()));
		album.setReleaseDate(createDate(source.getReleaseDate(), source.getReleaseDatePrecision()));
		// TODO add these fields
		source.getCopyrights();
		source.getExternalIds();
		source.getTracks();
		return album;
	}

	private LocalDate createDate(String releaseDate, ReleaseDatePrecision releaseDatePrecision) {
		final StringTokenizer tokens = new StringTokenizer(releaseDate, "-");
		final int year = Integer.valueOf(tokens.nextToken());
		final int month;
		switch(releaseDatePrecision) {
		case DAY:
			month = Integer.valueOf(tokens.nextToken());
			final int day = Integer.valueOf(tokens.nextToken());
			return LocalDate.of(year, month, day);
		case MONTH:
			month = Integer.valueOf(tokens.nextToken());
			return LocalDate.of(year, month, 1);
		case YEAR:
			return LocalDate.of(year, 1, 1);
		default:
			return null;
		}
	}

	private Album toAlbum(SimpleAlbum source, Set<Artist> artists) {
		final Pair<TypeRelease, String> pair = TypeRelease.parseAlbumName(source.getName(), toTypeRelease(source.getAlbumType()));
		final Album album = albumFactory.createSingleArtistAlbum(pair.getRight(), pair.getLeft(), artists);
		final Document spotify = new Document(ID, source.getId())
				.append(MARKETS, source.getAvailableMarkets())
				.append(URL, source.getHref())
				.append(URI, source.getUri());
		// TODO handle these fields
		source.getExternalUrls();
		final byte[] biggest = biggest(source.getImages());
		if(biggest != null) {
			album.setCover(biggest);
		}
		album.putExternalMetadata(SPOTIFY.name(), spotify);
		return album;
	}

	private byte[] biggest(List<Image> images) {
		final Image biggest = images.stream()
				.reduce(this::compare)
				.orElse(null);
		if(biggest != null) {
			return CrawlerIOUtils.downloadImage(biggest.getUrl());
		}
		return null;
	}

	private Image compare(Image left, Image right) {
		final int dimLeft = left.getHeight()*left.getWidth();
		final int dimRight = right.getHeight()*right.getWidth();
		return dimLeft > dimRight ? left : right;
	}

	private TypeRelease toTypeRelease(AlbumType albumType) {
		switch(albumType) {
		case ALBUM:
			return TypeRelease.STUDIO;
		case COMPILATION:
			return TypeRelease.COMPILATION;
		case SINGLE:
			return TypeRelease.SINGLE;
		case APPEARS_ON:
			return TypeRelease.COMPILATION;
		default:
			break;
		}
		return null;
	}

	@Override
	public Artist toArtist(com.wrapper.spotify.models.artist.Artist source, String originalName) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final Set<Genre> genres = Sets.newLinkedHashSetWithExpectedSize(source.getGenres().size());
		final Artist artist = bestMatchArtist(artists, originalName);
		for(String genreName : source.getGenres()) {
			genres.add(genreFactory.createGenre(genreName));
		}
		if(artist != null) {
			artist.setGenres(genres);
			final byte[] biggest = biggest(source.getImages());
			if(biggest != null) {
				artist.setPicture(biggest);
			}
			//TODO add these fields
			source.getFollowers();
			source.getId();
			source.getImages();
			source.getPopularity();
		}
		return artist;
	}

	private Set<Artist> flatArtist(SimpleArtist source) {
		final Set<Artist> flatArtists = artistFactory.getArtistsFromFormat(source.getName());
		for(Artist artist : flatArtists) {
			Document spotify = artist.getExternalMetadata(SPOTIFY.name());
			if(spotify == null) {
				spotify = new Document();
				artist.putExternalMetadata(SPOTIFY.name(), spotify);
			}
			spotify.append(ID, source.getId())
			.append(URL, source.getHref())
			.append(URI, source.getUri());
			//TODO add these fields
			source.getExternalUrls();
		}
		return flatArtists;
	}

	@Override
	public Set<Artist> toArtist(com.wrapper.spotify.models.artist.Artist source) {
		final Set<Artist> artists = flatArtist(source);
		final byte[] biggest = biggest(source.getImages());
		final Set<Genre> genres = Sets.newHashSetWithExpectedSize(source.getGenres().size());
		for(String genreName : source.getGenres()) {
			genres.add(genreFactory.createGenre(genreName));
		}
		for(Artist artist : artists) {
			final Document spotify = artist.getExternalMetadata(SPOTIFY.name());
			if(biggest != null) {
				artist.setPicture(biggest);
			}
			artist.setGenres(genres);
			spotify.append(POPULARITY, source.getPopularity())
			.append(LISTENERS, source.getFollowers());
		}
		return artists;
	}

}
