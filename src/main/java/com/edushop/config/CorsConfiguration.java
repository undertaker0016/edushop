package com.edushop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfiguration {
	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${edushop.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String configuredOrigins) {
		org.springframework.web.cors.CorsConfiguration cors = new org.springframework.web.cors.CorsConfiguration();
		List<String> origins = Arrays.stream(configuredOrigins.split(","))
				.map(origin -> origin == null ? "" : origin.trim()).filter(origin -> !origin.isEmpty()).toList();
		cors.setAllowedOrigins(origins);
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept"));
		cors.setExposedHeaders(List.of("Location"));
		cors.setAllowCredentials(false);
		cors.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
