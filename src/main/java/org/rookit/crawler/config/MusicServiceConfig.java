package org.rookit.crawler.config;

import static org.rookit.utils.config.ConfigUtils.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("javadoc")
public class MusicServiceConfig {
	
	private static final Path DEFAULT_CACHE_PATH = Paths.get("crawler", "cache");
	
	private String cachePath;
	private LastFMConfig lastfm;
	private SpotifyConfig spotify;

	public LastFMConfig getLastfm() {
		return getOrDefault(lastfm, new LastFMConfig());
	}

	public void setLastfm(LastFMConfig lastfm) {
		this.lastfm = lastfm;
	}

	public String getCachePath() {
		return getOrDefault(cachePath, DEFAULT_CACHE_PATH.toString());
	}

	public void setCachePath(String cachePath) {
		this.cachePath = cachePath;
	}

	public SpotifyConfig getSpotify() {
		return getOrDefault(spotify, new SpotifyConfig());
	}

	public void setSpotify(SpotifyConfig spotify) {
		this.spotify = spotify;
	}
	
}
