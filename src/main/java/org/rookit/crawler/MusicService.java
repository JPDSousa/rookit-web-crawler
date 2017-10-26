package org.rookit.crawler;

import java.util.stream.Stream;

import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.track.Track;

@SuppressWarnings("javadoc")
public interface MusicService {
	
	Stream<Track> searchTrack(Track track);
	Stream<Track> searchArtistTracks(Artist artist);
	Stream<Track> searchRelatedTracks(Track track);
	
	Stream<Artist> searchArtist(Artist artist);
	Stream<Artist> searchRelatedArtists(Artist artist);
	
	Stream<Album> searchAlbum(Album album);
	
	Stream<Genre> searchGenre(Genre genre);
	Stream<Genre> searchRelatedGenres(Genre genre);
	
}
