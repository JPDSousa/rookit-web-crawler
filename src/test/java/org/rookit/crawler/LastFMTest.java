package org.rookit.crawler;

import static org.junit.Assert.*;
import static org.hamcrest.number.OrderingComparison.*;

import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
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
	private static LevenshteinDistance distance;
	private static LastFMConfig config;

	@BeforeClass
	public static void setUpBeforeClass() {
		config = new LastFMConfig();
		config.setLevenshteinThreshold(5);
		distance = LevenshteinDistance.getDefaultInstance();
		factory = DMTestFactory.getDefault();
		lastFM = new LastFM(config);
		artistFactory = ArtistFactory.getDefault();
	}

	@Test
	public final void testSearchTrack() {
		final Track track = factory.getRandomTrack("hey brother");
		final Set<Artist> artists = Sets.newLinkedHashSet();
		artists.add(artistFactory.createArtist(TypeArtist.GROUP, "Avicii"));
		track.setMainArtists(artists);
		final long count = lastFM.searchTrack(track)
				//.peek(t -> System.out.println(PrintUtils.track(t)))
				.count();
		assertTrue(count > 0);
	}
	
	@Test
	public final void testSearchArtistTracks() {
		final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, "Tritonal");
		final long count = lastFM.searchArtistTracks(artist)
				//.peek(t -> System.out.println(PrintUtils.track(t)))
				.count();
		assertTrue(count > 0);
	}
	
	@Test
	public final void testSearchArtist() {
		final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, "Green Day");
		final long count = lastFM.searchArtist(artist)
				.map(a -> Pair.of(a, distance.apply(a.getName().toLowerCase(), artist.getName().toLowerCase())))
				.peek(a -> System.out.println(artist.getName() + "(" + a.getRight() + ")\n" + PrintUtils.artist(a.getLeft())))
				.map(Pair::getRight)
				.peek(score -> assertThat(score, lessThan(config.getLevenshteinThreshold())))
				.count();
		assertTrue(count > 0);
	}

}
