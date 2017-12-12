package org.rookit.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.rookit.crawler.config.SpotifyConfig;
import org.rookit.utils.resource.Resources;

@SuppressWarnings("javadoc")
public abstract class TestUtils {

	private static final Path SECRET_PATH = Resources.RESOURCES_TEST.resolve("client").resolve("secret.txt");
	
	public static final SpotifyConfig readSpotifyConfig() throws IOException {
		final BufferedReader reader = Files.newBufferedReader(SECRET_PATH);
		final SpotifyConfig sConfig = new SpotifyConfig();
		sConfig.setClientId(reader.readLine());
		sConfig.setClientSecret(reader.readLine());
		reader.close();
		return sConfig;
	}
	
	private TestUtils() {}
	
}
