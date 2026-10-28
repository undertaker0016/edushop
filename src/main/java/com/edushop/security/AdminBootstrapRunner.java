package com.edushop.security;

import com.edushop.entity.Role;
import com.edushop.entity.User;
import com.edushop.repository.RoleRepository;
import com.edushop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@Profile("!test")
public class AdminBootstrapRunner implements ApplicationRunner {

	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminEmail;
	private final String adminPassword;
	private final String adminFullName;

	public AdminBootstrapRunner(
			RoleRepository roleRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${EDUSHOP_BOOTSTRAP_ADMIN_EMAIL:}") String adminEmail,
			@Value("${EDUSHOP_BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword,
			@Value("${EDUSHOP_BOOTSTRAP_ADMIN_NAME:EduShop Administrator}") String adminFullName) {
		this.roleRepository = roleRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
		this.adminFullName = adminFullName;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		boolean emailProvided = !adminEmail.isBlank();
		boolean passwordProvided = !adminPassword.isBlank();
		if (!emailProvided && !passwordProvided) {
			return;
		}
		if (!emailProvided || !passwordProvided) {
			throw new IllegalStateException(
					"Set both EDUSHOP_BOOTSTRAP_ADMIN_EMAIL and EDUSHOP_BOOTSTRAP_ADMIN_PASSWORD, or leave both unset.");
		}
		int passwordBytes = adminPassword.getBytes(StandardCharsets.UTF_8).length;
		if (passwordBytes < 12 || passwordBytes > 72) {
			throw new IllegalStateException("Bootstrap admin password must be between 12 and 72 UTF-8 bytes.");
		}

		String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
		Role adminRole = roleRepository.findByName("ADMIN")
				.orElseThrow(() -> new IllegalStateException("Required ADMIN role is missing from the database."));

		User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);
		if (existingUser != null) {
			boolean alreadyAdministrator = existingUser.getRoles().stream()
					.anyMatch(role -> "ADMIN".equals(role.getName()));
			if (!alreadyAdministrator) {
				throw new IllegalStateException(
						"Bootstrap email belongs to an account without ADMIN role; refusing to elevate it.");
			}
			return;
		}

		User admin = new User(
				adminFullName.trim(),
				normalizedEmail,
				passwordEncoder.encode(adminPassword),
				null);
		admin.addRole(adminRole);
		userRepository.saveAndFlush(admin);
	}
}
