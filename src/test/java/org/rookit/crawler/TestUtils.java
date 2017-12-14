package org.rookit.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.rookit.crawler.config.MusicServiceConfig;
import org.rookit.crawler.config.SpotifyConfig;
import org.rookit.utils.resource.Resources;

@SuppressWarnings("javadoc")
public abstract class TestUtils {

	private static final Path SECRET_PATH = Resources.RESOURCES_TEST.resolve("client").resolve("secret.txt");
	private static final Path FORMATS_PATH = Resources.RESOURCES_TEST.resolve("parser").resolve("formats.txt");
	
	public static final MusicServiceConfig readConfig() throws IOException {
		final BufferedReader reader = Files.newBufferedReader(SECRET_PATH);
		final MusicServiceConfig config = new MusicServiceConfig();
		final SpotifyConfig sConfig = new SpotifyConfig();
		sConfig.setClientId(reader.readLine());
		sConfig.setClientSecret(reader.readLine());
		config.setFormatsPath(FORMATS_PATH.toString());
		config.setSpotify(sConfig);
		reader.close();
		return config;
	}
	
	private TestUtils() {}
	
}
