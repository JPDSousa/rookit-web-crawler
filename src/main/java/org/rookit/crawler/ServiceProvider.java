package org.rookit.crawler;

@SuppressWarnings("javadoc")
public interface ServiceProvider {
	
	MusicService getService(AvailableServices serviceKey);

}
