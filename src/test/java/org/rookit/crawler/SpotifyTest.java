package org.rookit.crawler;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.rookit.crawler.TestUtils.readSpotifyConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.rookit.crawler.config.SpotifyConfig;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.AlbumFactory;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.ArtistFactory;
import org.rookit.dm.artist.TypeArtist;
import org.rookit.dm.track.Track;
import org.rookit.dm.utils.DMTestFactory;
import org.rookit.dm.utils.PrintUtils;
import com.google.common.collect.Sets;

@SuppressWarnings("javadoc")
public class SpotifyTest {

	private static AlbumFactory albumFactory;
	private static ArtistFactory artistFactory;
	private static DMTestFactory factory;
	private static DB cache;
	private static Spotify spotify;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		final SpotifyConfig config = readSpotifyConfig();
		albumFactory = AlbumFactory.getDefault();
		artistFactory = ArtistFactory.getDefault();
		factory = DMTestFactory.getDefault();
		cache = DBMaker.memoryDB().make();
		spotify = new Spotify(config, cache);
	}

	@AfterClass
	public static void tearDown() {
		cache.close();
	}

	@Test
	public final void testSearchTrack() {
		final Track track = factory.getRandomTrack("hey brother");
		final Set<Artist> artists = Sets.newLinkedHashSet();
		artists.add(artistFactory.createArtist(TypeArtist.GROUP, "Avicii"));
		track.setMainArtists(artists);
		final long count = spotify.searchTrack(track)
				.count()
				.blockingGet();
		assertThat(count, is(greaterThan(0L)));
	}
	
	@Test
	public final void testSearchArtistTracks() {
		final Artist artist = spotify.searchArtist(artistFactory.createArtist(TypeArtist.GROUP, "Madeon"))
				.firstOrError()
				.blockingGet();
		
		final long count = spotify.getArtistTracks(artist).count().blockingGet();
		assertThat(count, is(greaterThan(0L)));
	}
	
	@Test
	public final void testSearchArtist() {
		final Artist artist = artistFactory.createArtist(TypeArtist.GROUP, "Green Day");
		final long count = spotify.searchArtist(artist)
				.count()
				.blockingGet();
		assertThat(count, is(greaterThan(0L)));
	}
	
	@Test
	public final void testSearchAlbum() {
		final Artist macklemore = artistFactory.createArtist(TypeArtist.GROUP, "Macklemore");
		final Artist ryan = artistFactory.createArtist(TypeArtist.GROUP, "Ryan Lewis");
		final Set<Artist> artists = Sets.newLinkedHashSet(Arrays.asList(macklemore, ryan));
		final Album album = albumFactory.createSingleArtistAlbum("The Heist", artists);
		final long count = spotify.searchAlbum(album)
				.map(PrintUtils::album)
				.doAfterNext(System.out::println)
				.count()
				.blockingGet();
		assertThat(count, is(greaterThan(0L)));
	}
	
	@Test
	public final void testTopTracks() {
		final long count = spotify.topTracks()
				.map(PrintUtils::track)
				.doAfterNext(System.out::println)
				.count()
				.blockingGet();
		assertThat(count, is(greaterThan(0L)));
	}
	
}
