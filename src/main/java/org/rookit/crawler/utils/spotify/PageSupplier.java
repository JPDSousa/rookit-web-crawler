package org.rookit.crawler.utils.spotify;

import java.util.function.Function;
import java.util.function.Supplier;

import com.wrapper.spotify.models.Page;

@SuppressWarnings("javadoc")
public class PageSupplier<T> implements Supplier<Page<T>> {
	
	private int offset;
	private final int pageSize;
	private final Function<Integer, Page<T>> supplier;
	
	public PageSupplier(int pageSize, Function<Integer, Page<T>> supplier) {
		super();
		this.offset = 0;
		this.supplier = supplier;
		this.pageSize = pageSize;
	}

	@Override
	public Page<T> get() {
		return supplier.apply(pageSize*offset++);
	}

}
