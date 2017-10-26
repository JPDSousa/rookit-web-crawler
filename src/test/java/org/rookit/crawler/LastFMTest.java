package org.rookit.crawler;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rookit.crawler.config.LastFMConfig;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.ArtistFactory;
import org.rookit.dm.artist.TypeArtist;
import org.rookit.dm.track.Track;
import org.rookit.dm.utils.DMTestFactory;
import org.rookit.dm.utils.PrintUtils;

import com.google.common.collect.Sets;

@SuppressWarnings("javadoc")
public class LastFMTest {
	
	private static MusicService lastFM;
	private static DMTestFactory factory;
	private static ArtistFactory artistFactory;

	@BeforeClass
	public static void setUpBeforeClass() {
		factory = DMTestFactory.getDefault();
		lastFM = new LastFM(new LastFMConfig());
		artistFactory = ArtistFactory.getDefault();
	}

	@Test
	public final void testSearchTrack() {
		final Track track = factory.getRandomTrack("hey brother");
		final Set<Artist> artists = Sets.newLinkedHashSet();
		artists.add(artistFactory.createArtist(TypeArtist.GROUP, "Avicii"));
		track.setMainArtists(artists);
		final long count = lastFM.searchTrack(track)
				.peek(t -> System.out.println(PrintUtils.track(t)))
				.count();
		assertTrue(count > 0);
	}
	
	@Test
	public final void testSearchArtistTracks() {
		final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, "Tritonal");
		final long count = lastFM.searchArtistTracks(artist)
				.peek(t -> System.out.println(PrintUtils.track(t)))
				.count();
		assertTrue(count > 0);
	}

}
