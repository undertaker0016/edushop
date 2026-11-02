package com.edushop.dto.auth;

public class AuthResponse {

	private final String accessToken;
	private final String tokenType;
	private final long expiresInSeconds;

	public AuthResponse(String accessToken, long expiresInSeconds) {
		this.accessToken = accessToken;
		this.tokenType = "Bearer";
		this.expiresInSeconds = expiresInSeconds;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getTokenType() {
		return tokenType;
	}

	public long getExpiresInSeconds() {
		return expiresInSeconds;
	}

	@Override
	public String toString() {
		return "AuthResponse{accessToken=[REDACTED], tokenType='" + tokenType
				+ "', expiresInSeconds=" + expiresInSeconds + "}";
	}
}
