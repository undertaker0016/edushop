package com.edushop.security;

import com.edushop.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final Clock clock;
	private final String issuer;
	private final Duration accessTokenTtl;

	public JwtTokenService(
			JwtEncoder jwtEncoder,
			@Value("${security.jwt.issuer:edushop}") String issuer,
			@Value("${security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
		if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
			throw new IllegalArgumentException("JWT access token lifetime must be positive");
		}
		this.jwtEncoder = jwtEncoder;
		this.clock = Clock.systemUTC();
		this.issuer = issuer;
		this.accessTokenTtl = accessTokenTtl;
	}

	public IssuedAccessToken issue(User user) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(accessTokenTtl);

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(user.getId().toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(token, accessTokenTtl.toSeconds());
	}

	public record IssuedAccessToken(String value, long expiresInSeconds) {

		@Override
		public String toString() {
			return "IssuedAccessToken{value=[REDACTED], expiresInSeconds="
					+ expiresInSeconds + "}";
		}
	}
}
