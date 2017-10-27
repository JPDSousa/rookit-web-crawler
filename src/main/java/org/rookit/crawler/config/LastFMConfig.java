package org.rookit.crawler.config;

import static org.rookit.utils.config.ConfigUtils.*;

@SuppressWarnings("javadoc")
public class LastFMConfig {
	
	private String apiKey;
	private String user;
	private int levenshteinThreshold;
	private boolean debug;
	
	public String getApiKey() {
		return getOrDefault(apiKey, "4c0824b3aa2f1114eee0ca8259512071");
	}
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	public String getUser() {
		return getOrDefault(user, "kryptonkirk");
	}
	public void setUser(String user) {
		this.user = user;
	}
	public int getLevenshteinThreshold() {
		return getOrDefault(levenshteinThreshold, 8);
	}
	public void setLevenshteinThreshold(int levenshteinThreshold) {
		this.levenshteinThreshold = levenshteinThreshold;
	}
	public boolean isDebug() {
		return getOrDefault(debug, false);
	}
	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	

}
