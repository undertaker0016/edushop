package com.edushop.security;

import com.edushop.entity.Role;
import com.edushop.entity.User;
import com.edushop.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DatabaseUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

		List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
				.map(Role::getName)
				.map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
				.toList();

		return org.springframework.security.core.userdetails.User
				.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.authorities(authorities)
				.disabled(!"ACTIVE".equals(user.getStatus()))
				.build();
	}
}
