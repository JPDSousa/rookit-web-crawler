package org.rookit.crawler;

import java.util.concurrent.Callable;

import com.google.common.util.concurrent.RateLimiter;

abstract class AbstractMusicService implements MusicService {

	protected final RateLimiter limiter;
	
	public AbstractMusicService(int rateLimit) {
		limiter = RateLimiter.create(rateLimit);
	}
	
	protected <T> T schedule(Callable<T> executor) {
		limiter.acquire();
		try {
			return executor.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
