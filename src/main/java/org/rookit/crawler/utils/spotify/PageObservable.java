package org.rookit.crawler.utils.spotify;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.mockito.Mockito;

import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.Request;
import com.wrapper.spotify.models.page.Page;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

@SuppressWarnings("javadoc")
public class PageObservable<T> implements ObservableOnSubscribe<T> {
	
	private static final Logger LOGGER = Logger.getLogger(PageObservable.class.getName());
	
	@SuppressWarnings("unchecked")
	private static <I> Request<I> createDummyRequest(I item2Return) {
		final Request<I> request = Mockito.mock(Request.class);
		try {
			Mockito.when(request.exec()).thenReturn(item2Return);
			return request;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private final Api api;
	private final Request<Page<T>> firstPage;
	
	public PageObservable(Api api, Request<Page<T>> firstPage) {
		super();
		this.api = api;
		this.firstPage = firstPage;
	}
	
	public PageObservable(Api api, Page<T> firstPage) {
		this(api, createDummyRequest(firstPage));
	}

	@Override
	public void subscribe(ObservableEmitter<T> emitter) throws Exception {
		final ExecutorService pool = Executors.newSingleThreadExecutor();
		pool.execute(() -> {
			Request<Page<T>> currentRequest = firstPage;
			while(currentRequest != null) {
				try {
					final Page<T> response = currentRequest.exec();
					for(T item : response.getItems()) {
						emitter.onNext(item);
					}
					currentRequest = api.getNextPage(response);
				} catch (IOException e) {
					emitter.onError(e);
					LOGGER.severe(e.getMessage());
				}				
			}
			emitter.onComplete();
		});
		pool.shutdown();
	}
	
}
