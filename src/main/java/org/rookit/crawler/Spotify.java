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
import org.rookit.crawler.factory.SpotifyFactory;
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
	private final SpotifyFactory factory;
	
	public Spotify() {
		super(20);
		// TODO add config
		factory = new SpotifyFactory();
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
				.map(factory::toTrack);
	}
	
	private Page<com.wrapper.spotify.models.Track> searchByTitle(int limit, int offset, String title) {
		try {
			return api.searchTracks(encode(title)).limit(limit).offset(offset).build().get();
		} catch (IOException | WebApiException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Stream<Track> searchArtistTracks(Artist artist) {
		final String query = "artist:" + artist.getName();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByTitle(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(factory::toTrack);
	}

	@Override
	public Stream<Track> searchRelatedTracks(Track track) {
		final String query = track.getTitle().toString();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByTitle(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(factory::toTrack);
	}

	@Override
	public Stream<Artist> searchArtist(Artist artist) {
		final String query = artist.getName();
		return Stream.generate(new PageSupplier<>(limit, offset -> searchByArtist(limit, offset, query)))
				.map(Page::getItems)
				.flatMap(Collection::parallelStream)
				.map(a -> factory.toArtist(a, query));
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
