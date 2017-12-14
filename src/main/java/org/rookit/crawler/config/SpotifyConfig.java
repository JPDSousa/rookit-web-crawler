package org.rookit.crawler.config;

@SuppressWarnings("javadoc")
public class SpotifyConfig {

	private String clientId;
	private String clientSecret;
	private int rateLimit;
	
	public String getClientId() {
		return clientId;
	}
	
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	
	public String getClientSecret() {
		return clientSecret;
	}
	
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public int getRateLimit() {
		if(rateLimit > 0) {
			return rateLimit;
		}
		return 20;
	}

	public void setRateLimit(int rateLimit) {
		this.rateLimit = rateLimit;
	}
	
	
}
