package org.rookit.crawler.factory;

import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.track.Track;

@SuppressWarnings("javadoc")
public interface ModelFactory<Ar, Al, Tr> {
	
	Track toTrack(Tr source);
	
	Album toAlbum(Al source);
	
	Artist toArtist(Ar source, String originalName);
	
}
