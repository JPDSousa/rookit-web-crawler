package org.rookit.crawler;

import java.util.Map;

import org.rookit.crawler.config.MusicServiceConfig;

import com.google.common.collect.Maps;

import static org.rookit.crawler.AvailableServices.*;

class ServiceProviderImpl implements ServiceProvider {
	
	private final Map<AvailableServices, MusicService> activeServices;
	
	ServiceProviderImpl(MusicServiceConfig config) {
		activeServices = Maps.newHashMapWithExpectedSize(AvailableServices.values().length);
		activeServices.put(LASTFM, new LastFM(config.getLastfm()));
	}

	@Override
	public MusicService getService(AvailableServices serviceKey) {
		return null;
	}

}
