package org.rookit.crawler.config;

import static org.rookit.utils.config.ConfigUtils.*;

@SuppressWarnings("javadoc")
public class MusicServiceConfig {
	
	private LastFMConfig lastfm;

	public LastFMConfig getLastfm() {
		return getOrDefault(lastfm, new LastFMConfig());
	}

	public void setLastfm(LastFMConfig lastfm) {
		this.lastfm = lastfm;
	}
	
	

}
