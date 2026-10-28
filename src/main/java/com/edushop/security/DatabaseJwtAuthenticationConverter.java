package com.edushop.security;

import com.edushop.entity.Role;
import com.edushop.entity.User;
import com.edushop.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

	private final UserRepository userRepository;

	public DatabaseJwtAuthenticationConverter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public JwtAuthenticationToken convert(Jwt jwt) {
		final long userId;
		try {
			userId = Long.parseLong(jwt.getSubject());
		} catch (NumberFormatException exception) {
			throw new InvalidBearerTokenException("Invalid bearer token");
		}

		User user = userRepository.findById(userId)
				.filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
				.orElseThrow(() -> new InvalidBearerTokenException("Invalid bearer token"));

		List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
				.map(Role::getName)
				.map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
				.toList();

		return new JwtAuthenticationToken(jwt, authorities, user.getEmail());
	}
}
