package org.rookit.crawler;

import org.rookit.crawler.config.MusicServiceConfig;

@SuppressWarnings("javadoc")
public class ServiceProviderFactory {
	
	private static ServiceProviderFactory singleton;
	
	public static synchronized ServiceProviderFactory getDefault() {
		if(singleton == null) {
			singleton = new ServiceProviderFactory();
		}
		return singleton;
	}
	
	private ServiceProvider serviceProvider;
	
	private ServiceProviderFactory() {}
	
	public synchronized ServiceProvider getOrCreate(MusicServiceConfig config) {
		if(serviceProvider == null) {
			serviceProvider = new ServiceProviderImpl(config);
		}
		return serviceProvider;
	}

}
