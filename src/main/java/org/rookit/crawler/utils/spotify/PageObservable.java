package org.rookit.crawler.utils.spotify;

import java.io.IOException;
import java.util.logging.Logger;

import org.mockito.Mockito;

import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.Request;
import com.wrapper.spotify.models.page.Page;

import io.reactivex.Emitter;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;

@SuppressWarnings("javadoc")
public class PageObservable<T> implements ObservableOnSubscribe<T> {

	private static final Logger LOGGER = Logger.getLogger(PageObservable.class.getName());
	
	public static <T> PageObservable<T> create(Api api, Request<Page<T>> request) {
		return new PageObservable<>(api, request);
	}
	
	public static <T> PageObservable<T> create(Api api, Page<T> page) {
		return new PageObservable<>(api, createDummyRequest(page));
	}

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

	private PageObservable(Api api, Request<Page<T>> firstPage) {
		super();
		this.api = api;
		this.firstPage = firstPage;
	}

	public void drainTo(Emitter<T> emitter) {
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
	}
	
	public void drainTo(Observer<T> emitter) {
		drainTo(new AsEmitter(emitter));
	}

	@Override
	public void subscribe(ObservableEmitter<T> e) throws Exception {
		drainTo(e);
		e.onComplete();
	}
	
	private class AsEmitter implements Emitter<T> {
		
		private final Observer<T> observer;

		private AsEmitter(Observer<T> observer) {
			super();
			this.observer = observer;
		}

		@Override
		public void onNext(T value) {
			observer.onNext(value);
		}

		@Override
		public void onError(Throwable error) {
			observer.onError(error);
		}

		@Override
		public void onComplete() {
			observer.onComplete();
		}
		
		
	}
	
	

}
