package org.rookit.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.rookit.crawler.config.MusicServiceConfig;

import com.google.common.collect.Maps;

import static org.rookit.crawler.AvailableServices.*;

class ServiceProviderImpl implements ServiceProvider {

	private final DB cache; 
	private final Map<AvailableServices, MusicService> activeServices;

	ServiceProviderImpl(MusicServiceConfig config) {
		cache = DBMaker.fileDB(new File(config.getCachePath()))
				.fileMmapEnable()
				.make();
		activeServices = Maps.newHashMapWithExpectedSize(AvailableServices.values().length);
		//			activeServices.put(LASTFM, new LastFM(config.getLastfm()));
		activeServices.put(SPOTIFY, new Spotify(config, cache));
	}

	@Override
	public Optional<MusicService> getService(AvailableServices serviceKey) {
		return Optional.ofNullable(activeServices.get(serviceKey));
	}

	@Override
	public void close() throws IOException {
		cache.close();
	}

}
