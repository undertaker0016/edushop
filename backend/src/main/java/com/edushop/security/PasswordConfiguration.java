package com.edushop.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		var encoder = (org.springframework.security.crypto.password.DelegatingPasswordEncoder)
                PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // Existing BCrypt hashes may predate the {bcrypt} storage prefix.
        encoder.setDefaultPasswordEncoderForMatches(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
        return encoder;
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
