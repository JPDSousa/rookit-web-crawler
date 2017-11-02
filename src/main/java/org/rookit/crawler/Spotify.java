package org.rookit.crawler;

import static org.rookit.crawler.AvailableServices.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.rookit.crawler.utils.CrawlerIOUtils;
import org.rookit.crawler.utils.spotify.PageSupplier;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.TypeRelease;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;

import com.google.common.collect.Sets;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.models.AlbumType;
import com.wrapper.spotify.models.Image;
import com.wrapper.spotify.models.Page;
import com.wrapper.spotify.models.SimpleAlbum;
import com.wrapper.spotify.models.SimpleArtist;

class Spotify extends AbstractMusicService {
	
	private final Api api;
	private final int limit;
	
	public Spotify() {
		super(20);
		// TODO add config
		limit = 30;
		api = Api.builder()
				.clientId("366eacf6d13f424aa64f3138def16062")
				.clientSecret("9a723fbc54ca4bb1a24601c1ba59e286")
				.build();
	}
	
	private String encode(String source) {
		return source.replace(" ", "+");
	}

	@Override
	public Stream<Track> searchTrack(Track track) {
		final String title = track.getTitle().toString();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByTitle(limit, offset, title)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
	}
	
	private Page<com.wrapper.spotify.models.Track> searchByTitle(int limit, int offset, String title) {
		try {
			return api.searchTracks(encode(title)).limit(limit).offset(offset).build().get();
		} catch (IOException | WebApiException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Track toTrack(com.wrapper.spotify.models.Track source) {
		// TODO add these fields
		source.getExternalIds();
		source.getExternalUrls();
		source.getAlbum();
		final Document spotify = new Document(ID, source.getId())
				.append(POPULARITY, source.getPopularity())
				.append(MARKETS, source.getAvailableMarkets())
				.append(URL, source.getHref())
				.append(PREVIEW, source.getPreviewUrl())
				.append(URI, source.getUri());
		final Set<Artist> artists = toArtists(source.getArtists());
		return parser.parse(source.getName())
				.withMainArtists(artists)
				.withAlbum(toAlbum(source.getAlbum(), artists))
				.withDisc(source.getDiscNumber()+"")
				.withNumber(source.getTrackNumber())
				.withDuration(Duration.ofMillis(source.getDuration()))
				.withExplicit(source.isExplicit())
				.withExternalMetadata(SPOTIFY.name(), spotify)
				.getTrack();
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
		album.setCover(biggest(source.getImages()));
		album.putExternalMetadata(SPOTIFY.name(), spotify);
		return album;
	}

	private TypeRelease toTypeRelease(AlbumType albumType) {
		switch(albumType) {
		case ALBUM:
			return TypeRelease.STUDIO;
		case COMPILATION:
			return TypeRelease.COMPILATION;
		case SINGLE:
			return TypeRelease.SINGLE;
		}
		return null;
	}

	private Set<Artist> toArtists(List<SimpleArtist> source) {
		final Set<Artist> artists = Sets.newLinkedHashSetWithExpectedSize(source.size());
		for(SimpleArtist artist : source) {
			artists.addAll(artistFactory.getArtistsFromFormat(artist.getName()));
			// TODO add these fields
			artist.getId();
		}
		return artists;
	}

	@Override
	public Stream<Track> searchArtistTracks(Artist artist) {
		final String query = "artist:" + artist.getName();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByTitle(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
	}

	@Override
	public Stream<Track> searchRelatedTracks(Track track) {
		final String query = track.getTitle().toString();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByTitle(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(this::toTrack);
	}

	@Override
	public Stream<Artist> searchArtist(Artist artist) {
		final String query = artist.getName();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByArtist(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(this::toArtists)
				.flatMap(Collection::parallelStream);
	}
	
	private Set<Artist> toArtists(com.wrapper.spotify.models.Artist source) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final Set<Genre> genres = Sets.newLinkedHashSetWithExpectedSize(source.getGenres().size());
		for(String genreName : source.getGenres()) {
			genres.add(genreFactory.createGenre(genreName));
		}
		for(Artist artist : artists) {
			artist.setGenres(Sets.newLinkedHashSet(genres));
			artist.setPicture(biggest(source.getImages()));
			source.getFollowers();
			source.getId();
			source.getImages();
			source.getPopularity();
		}
		return artists;
	}
	
	private byte[] biggest(List<Image> images) {
		final Image biggest = images.stream()
				.reduce(this::compare)
				.get();
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

	private Page<com.wrapper.spotify.models.Artist> searchByArtist(int limit, int offset, String query) {
		try {
			return api.searchArtists(encode(query)).limit(limit).offset(offset).build().get();
		} catch (IOException | WebApiException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Stream<Artist> searchRelatedArtists(Artist artist) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Stream<Album> searchAlbum(Album album) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Stream<Genre> searchGenre(Genre genre) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Stream<Genre> searchRelatedGenres(Genre genre) {
		// TODO Auto-generated method stub
		return null;
	}

}
