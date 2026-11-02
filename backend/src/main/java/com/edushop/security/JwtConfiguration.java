package com.edushop.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfiguration {

	@Bean
	SecretKey jwtSecretKey(@Value("${security.jwt.secret-base64}") String secretBase64) {
		if (secretBase64.isBlank()) {
			throw new IllegalStateException(
					"Set EDUSHOP_JWT_SECRET_BASE64 to a Base64-encoded secret of at least 32 bytes.");
		}

		final byte[] secretBytes;
		try {
			secretBytes = Base64.getDecoder().decode(secretBase64);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("EDUSHOP_JWT_SECRET_BASE64 must be valid Base64.", exception);
		}

		if (secretBytes.length < 32) {
			throw new IllegalStateException("The JWT HMAC secret must contain at least 32 decoded bytes.");
		}

		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey)
				.keyID("edushop-hs256")
				.algorithm(JWSAlgorithm.HS256)
				.build();
		JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	JwtDecoder jwtDecoder(
			SecretKey jwtSecretKey,
			@Value("${security.jwt.issuer:edushop}") String issuer) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}
}
