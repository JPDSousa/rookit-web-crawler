package org.rookit.crawler.factory;

import java.util.Set;

import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.track.Track;

@SuppressWarnings("javadoc")
public interface ModelFactory<Ar, Al, Tr> {
	
	Track toTrack(Tr source);
	
	Album toAlbum(Al source);
	
	Artist toArtist(Ar source, String originalName);
	
	Set<Artist> toArtist(Ar source);
	
}
