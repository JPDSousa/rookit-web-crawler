package org.rookit.crawler;

import static org.rookit.crawler.AvailableServices.SPOTIFY;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.mapdb.DB;
import org.rookit.crawler.config.MusicServiceConfig;
import org.rookit.crawler.config.SpotifyConfig;
import org.rookit.crawler.factory.SpotifyFactory;
import org.rookit.crawler.utils.spotify.PageObservable;
import org.rookit.dm.MetadataHolder;
import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.Request;
import com.wrapper.spotify.methods.albums.AlbumsRequest;
import com.wrapper.spotify.methods.audiofeatures.AudioFeaturesRequest;
import com.wrapper.spotify.methods.tracks.TracksRequest;
import com.wrapper.spotify.models.album.SimpleAlbum;
import com.wrapper.spotify.models.artist.SimpleArtist;
import com.wrapper.spotify.models.audio.AudioFeature;
import com.wrapper.spotify.models.authentication.ClientCredentials;
import com.wrapper.spotify.models.playlist.Playlist;
import com.wrapper.spotify.models.playlist.PlaylistTrack;
import com.wrapper.spotify.models.track.SimpleTrack;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

@SuppressWarnings("javadoc")
public class Spotify implements MusicService {

	private static final Logger LOGGER = Logger.getLogger(Spotify.class.getName());

	private final Api api;
	private final SpotifyFactory factory;
	private final Scheduler requestScheduler;

	public Spotify(MusicServiceConfig config, DB cache) {
		if(cache == null) {
			LOGGER.warning("No cache provided");
		}
		final SpotifyConfig sConfig = config.getSpotify();
		final RateLimiter rateLimiter = RateLimiter.create(sConfig.getRateLimit());
		final ClientCredentials credentials;
		this.requestScheduler = Schedulers.io();
		factory = new SpotifyFactory(config);
		try {
			credentials = Api.builder()
					.clientId(sConfig.getClientId())
					.clientSecret(sConfig.getClientSecret())
					.build()
					.clientCredentialsGrant()
					.build()
					.exec();
			api = Api.builder()
					.accessToken(credentials.getAccessToken())
					.cache(cache)
					.rateLimiter(rateLimiter)
					.build();
			LOGGER.info("Spotify access token: " + credentials.getAccessToken());
			LOGGER.info("Spotify token expires in " + credentials.getExpiresIn() + " seconds");
		} catch (IOException e) {
			throw new RuntimeException("Cannot connect...", e);
		}
		LOGGER.info("Spotify crawler created");
	}

	public Scheduler getRequestScheduler() {
		return requestScheduler;
	}

	@Override
	public String getName() {
		return SPOTIFY.name();
	}

	@Override
	public Observable<Track> searchTrack(Track track) {
		final Artist artist = Iterables.getFirst(track.getMainArtists(), null);
		final String query = new StringBuilder("track:")
				.append(track.getTitle().toString())
				.append(" artist:")
				.append(artist != null ? artist.getName() : "*")
				.toString();
		LOGGER.info("Searching for track '" + track.getLongFullTitle() + "' with query: " + query);
		
		return Observable.create(PageObservable.create(api, api.searchTracks(query).build()))
				.buffer(AudioFeaturesRequest.MAX_IDS)
				.doAfterNext(tracks -> LOGGER.info("More " + tracks.size() + " results for track: " + track.getIdAsString()))
				.flatMap(this::getAudioFeatures);
	}

	private Observable<Track> getAudioFeatures(List<com.wrapper.spotify.models.track.Track> tracks) {
		try {
			final Map<String, com.wrapper.spotify.models.track.Track> groupedTracks = tracks.stream()
					.collect(Collectors.toMap(com.wrapper.spotify.models.track.Track::getId, track -> track));
			final List<String> ids = Lists.newArrayList(groupedTracks.keySet());
			final Map<String, AudioFeature> audioFeatures = api.getAudioFeatures(ids).build().exec().stream()
					.collect(Collectors.toMap(AudioFeature::getId, audioFeature -> audioFeature));
			return Observable.fromIterable(groupedTracks.keySet())
					.map(id -> factory.toTrack(groupedTracks.get(id), audioFeatures.get(id)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Observable<Track> getArtistTracks(Artist artist) {
		final String id = getId(artist);
		if(id == null) {
			throw new RuntimeException("Cannot find id for artist: " + artist.getName());
		}
		LOGGER.info("Fetching for artist tracks: " + id);

		return Observable.create(PageObservable.create(api, api.getAlbumsForArtist(id).build()))
				.observeOn(getRequestScheduler())
				.map(SimpleAlbum::getId)
				.distinctUntilChanged()
				.buffer(AlbumsRequest.MAX_IDS)
				.map(ids -> api.getAlbums(ids).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(com.wrapper.spotify.models.album.Album::getTracks)
				.flatMap(page -> Observable.create(PageObservable.create(api, page))
						.observeOn(getRequestScheduler()))
				.filter(t -> containsArtist(t, id))
				.map(SimpleTrack::getId)
				.buffer(TracksRequest.MAX_IDS)
				.map(ids -> api.getTracks(ids).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(factory::toTrack);
	}

	private boolean containsArtist(SimpleTrack t, String id) {
		return t.getArtists().stream()
				.map(SimpleArtist::getId)
				.anyMatch(i -> Objects.equal(i, id));
	}

	private String getId(MetadataHolder element) {
		final Map<String, Object> doc = element.getExternalMetadata(getName());
		return doc != null ? (String) doc.get(ID) : null;
	}

	@Override
	public Observable<Artist> searchArtist(Artist artist) {
		final String query = artist.getName();
		LOGGER.info("Searching artist with query: " + query);
		return Observable.create(PageObservable.create(api, api.searchArtists(query).build()))
				.observeOn(getRequestScheduler())
				.map(factory::toArtist)
				.flatMap(Observable::fromIterable);
	}

	@Override
	public Observable<Artist> searchRelatedArtists(Artist artist) {
		final String id = getId(artist);
		if(id == null) {
			throw new RuntimeException("Cannot find id for artist: " + artist.getName());
		}
		LOGGER.info("Searching for related artists of artist: " + id);
		return Observable.just(api.getArtistRelatedArtists(id).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(factory::toArtist)
				.flatMap(Observable::fromIterable);
	}

	@Override
	public Observable<Album> searchAlbum(Album album) {
		final Artist artist = Iterables.getFirst(album.getArtists(), null);
		final StringBuilder query = new StringBuilder("album:")
				.append(album.getTitle())
				.append(" artist:")
				.append(artist != null ? artist.getName() : "*");
		LOGGER.info("Searching for albums with query: " + query);
		return Observable.create(PageObservable.create(api, api.searchAlbums(query.toString()).build()))
				.observeOn(getRequestScheduler())
				.map(SimpleAlbum::getId)
				.buffer(AlbumsRequest.MAX_IDS)
				.map(ids -> api.getAlbums(ids).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(factory::toAlbum);
	}

	@Override
	public Observable<Track> getAlbumTracks(Album album) {
		final String id = getId(album);
		if(id == null) {
			throw new RuntimeException("Cannot find id for album: " + album.getTitle());
		}
		LOGGER.info("Searching for related artists of artist: " + id);
		return Observable.just(api.getAlbum(id).build())
				.flatMap(this::asyncRequest)
				.flatMap(a -> Observable.create(PageObservable.create(api, a.getTracks()))
						.observeOn(getRequestScheduler()))
				.map(SimpleTrack::getId)
				.buffer(TracksRequest.MAX_IDS)
				.map(ids -> api.getTracks(ids).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(factory::toTrack);
	}

	private <T> ObservableSource<T> asyncRequest(Request<T> request) {
		return Observable.fromCallable(() -> request.exec())
				.observeOn(getRequestScheduler());
	}

	@Override
	public Observable<Genre> searchGenre(Genre genre) {
		LOGGER.info("Searching genres is disabled in spotify crawler");
		return Observable.empty();
	}

	@Override
	public Observable<Genre> searchRelatedGenres(Genre genre) {
		LOGGER.info("Searching related genres is disabled in spotify crawler");
		return Observable.empty();
	}

	@Override
	public Observable<Track> topTracks() {
		LOGGER.info("Fetching top tracks");
		return Observable.just(api.getPlaylist("spotify", "37i9dQZF1DXcBWIGoYBM5M").build())
				.flatMap(this::asyncRequest)
				.map(Playlist::getTracks)
				.flatMap(page -> Observable.create(PageObservable.create(api, page))
						.observeOn(getRequestScheduler()))
				.map(PlaylistTrack::getTrack)
				.map(factory::toTrack);
	}

	@Override
	public Observable<Artist> topArtists() {
		LOGGER.info("Fetching top artists");
		return Observable.just(api.getPlaylist("spotifycharts", "37i9dQZEVXbMDoHDwVN2tF").build())
				.flatMap(this::asyncRequest)
				.map(Playlist::getTracks)
				.flatMap(page -> Observable.create(PageObservable.create(api, page))
						.observeOn(getRequestScheduler()))
				.map(PlaylistTrack::getTrack)
				.map(factory::toTrack)
				.map(Track::getMainArtists)
				.flatMap(Observable::fromIterable);
	}

	@Override
	public Observable<Album> topAlbums() {
		LOGGER.info("Fetching top albums");
		return Observable.just(api.getPlaylist("spotify", "37i9dQZF1DX0rV7skaITBo").build())
				.flatMap(this::asyncRequest)
				.map(Playlist::getTracks)
				.flatMap(page -> Observable.create(PageObservable.create(api, page))
						.observeOn(getRequestScheduler()))
				.map(PlaylistTrack::getTrack)
				.map(com.wrapper.spotify.models.track.Track::getAlbum)
				.map(SimpleAlbum::getId)
				.buffer(AlbumsRequest.MAX_IDS)
				.map(ids -> api.getAlbums(ids).build())
				.flatMap(this::asyncRequest)
				.flatMap(Observable::fromIterable)
				.map(factory::toAlbum);
	}

}
