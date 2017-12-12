package org.rookit.crawler;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rookit.crawler.config.MusicServiceConfig;
import org.rookit.crawler.config.SpotifyConfig;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.ArtistFactory;
import org.rookit.dm.artist.TypeArtist;
import org.rookit.dm.track.Track;
import org.rookit.parser.result.SingleTrackAlbumBuilder;
import org.rookit.utils.resource.Resources;

import com.google.common.collect.Sets;

@SuppressWarnings("javadoc")
public class RookitCrawlerTest {

	private static final Path SECRET_PATH = Resources.RESOURCES_TEST.resolve("client").resolve("secret.txt");
	
	private static RookitCrawler guineaPig;
	private static ArtistFactory artistFactory;

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		final BufferedReader reader = Files.newBufferedReader(SECRET_PATH);
		final SpotifyConfig sConfig = new SpotifyConfig();
		final MusicServiceConfig config = new MusicServiceConfig();
		config.setSpotify(sConfig);
		sConfig.setClientId(reader.readLine());
		sConfig.setClientSecret(reader.readLine());
		reader.close();
		artistFactory = ArtistFactory.getDefault();
		guineaPig = new RookitCrawler(config);
	}
	
	@AfterClass
	public static final void tearDownAfterClass() throws IOException {
		guineaPig.close();
	}

	@Test
	public final void testFillTrack() {
		final Artist u2 = artistFactory.createArtist(TypeArtist.GROUP, "U2");
		final Set<Artist> artists = Sets.newHashSet(u2);
		final Track track = SingleTrackAlbumBuilder.create()
				.withTitle("One")
				.withMainArtists(artists)
				.getTrack();
		guineaPig.fillTrack(track).blockingAwait();
		
		assertNotNull(track.getExternalMetadata(AvailableServices.SPOTIFY.name()));
	}
}
