package org.rookit.crawler;

import static org.rookit.crawler.AvailableServices.*;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.rookit.crawler.config.LastFMConfig;
import org.rookit.crawler.factory.LastFMFactory;
import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;

import de.umass.lastfm.Caller;

class LastFM extends AbstractMusicService {

	private final String apiKey;
	private final String user;
	private final LastFMFactory factory;

	LastFM(LastFMConfig config) {
		super(5);
		factory = new LastFMFactory(config.getLevenshteinThreshold());
		this.apiKey = config.getApiKey();
		this.user = config.getUser();
		final Caller caller = Caller.getInstance();
		caller.setUserAgent("tst");
		caller.getLogger().setUseParentHandlers(config.isDebug());
	}

	@Override
	public Stream<Track> searchTrack(Track track) {
		final Document mBrainz = track.getExternalMetadata(MBRAINZ.name());
		final Stream<de.umass.lastfm.Track> rawStream;
		if(mBrainz != null && mBrainz.getString(ID) != null) {
			final String id = mBrainz.getString(ID);
			rawStream = Stream.of(lookupTrack(id));
		}
		else {
			final String trackTitle = track.getTitle().toString();
			rawStream = StreamSupport.stream(track.getMainArtists().spliterator(), true)
					.map(Artist::getName)
					.map(name -> searchTrack(trackTitle, name))
					.flatMap(Collection::parallelStream); 
		}
		return rawStream.map(factory::toTrack)
				.filter(Objects::nonNull);
	}

	private Collection<de.umass.lastfm.Track> searchTrack(final String trackTitle, String name) {
		return schedule(() -> de.umass.lastfm.Track.search(name, trackTitle, 200, apiKey));
	}

	private de.umass.lastfm.Track lookupTrack(String id) {
		return schedule(() -> de.umass.lastfm.Track.getInfo(null, id, apiKey));
	}

	@Override
	public Stream<Track> searchArtistTracks(Artist artist) {
		final Stream<de.umass.lastfm.Track> topTracks = getArtistTopTracks(artist)
				.parallelStream();
		final Stream<de.umass.lastfm.Track> topAlbums = getArtistTopAlbums(artist)
				.parallelStream()
				.map(de.umass.lastfm.Album::getTracks)
				.flatMap(Collection::parallelStream);
		return Stream.concat(topTracks, topAlbums)
				.map(factory::toTrack)
				.filter(Objects::nonNull);
	}
	
	private Collection<de.umass.lastfm.Album> getArtistTopAlbums(Artist artist) {
		final String artistName = artist.getName();
		return schedule(() -> de.umass.lastfm.Artist.getTopAlbums(artistName, apiKey));
	}

	private Collection<de.umass.lastfm.Artist> searchArtistByName(Artist artist) {
		return schedule(() -> de.umass.lastfm.Artist.search(artist.getName(), apiKey));
	}

	private Collection<de.umass.lastfm.Track> getArtistTopTracks(Artist artist) {
		return schedule(() -> de.umass.lastfm.Artist.getTopTracks(artist.getName(), apiKey));
	}

	@Override
	public Stream<Artist> searchArtist(Artist artist) {
		return searchArtistByName(artist).parallelStream()
				.map(artistRes -> factory.toArtist(artistRes, artist.getName()))
				.filter(Objects::nonNull);
	}

	@Override
	public Stream<Album> searchAlbum(Album album) {
		final Stream<de.umass.lastfm.Album> rawStream;
		final Document mBrainz = album.getExternalMetadata(MBRAINZ.name());
		final String id = mBrainz != null ? mBrainz.getString(ID) : null;
		if(id != null) {
			rawStream = Stream.of(lookupAlbum(id));
		}
		else {
			rawStream = searchAlbumByName(album).parallelStream();
		}
		return rawStream.map(factory::toAlbum)
				.filter(Objects::nonNull);
	}

	private Collection<de.umass.lastfm.Album> searchAlbumByName(Album album) {
		return schedule(() -> de.umass.lastfm.Album.search(album.getTitle(), apiKey));
	}
	
	private de.umass.lastfm.Album lookupAlbum(final String id) {
		return de.umass.lastfm.Album.getInfo(null, id, apiKey);
	}

	@Override
	public Stream<Genre> searchGenre(Genre genre) {
		return Stream.empty();
	}

	@Override
	public Stream<Track> searchRelatedTracks(Track track) {
		final Document mBrainz = track.getExternalMetadata(MBRAINZ.name());
		final String id = mBrainz != null ? mBrainz.getString(ID) : track.getTitle().toString();
		return StreamSupport.stream(track.getMainArtists().spliterator(), true)
				.map(Artist::getName)
				.map(artistName -> schedule(() -> de.umass.lastfm.Track.getSimilar(artistName, id, apiKey)))
				.flatMap(Collection::parallelStream)
				.map(factory::toTrack);
	}

	@Override
	public Stream<Artist> searchRelatedArtists(Artist artist) {
		return de.umass.lastfm.Artist.getSimilar(artist.getName(), apiKey)
				.parallelStream()
				.map(a -> factory.toArtist(a, artist.getName()));
	}

	@Override
	public Stream<Genre> searchRelatedGenres(Genre genre) {
		return Stream.empty();
	}
}
