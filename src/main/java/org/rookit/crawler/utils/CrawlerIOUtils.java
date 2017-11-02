package org.rookit.crawler.utils;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

@SuppressWarnings("javadoc")
public class CrawlerIOUtils {
	
	public static byte[] downloadImage(String urlStr) {
		try {
			final URL url = new URL(urlStr);
			return IOUtils.toByteArray(url.openStream());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
