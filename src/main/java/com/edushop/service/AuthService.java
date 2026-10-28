package com.edushop.service;

import com.edushop.dto.auth.AuthResponse;
import com.edushop.dto.auth.LoginRequest;
import com.edushop.dto.auth.RegistrationRequest;
import com.edushop.dto.auth.UserProfileResponse;
import com.edushop.entity.Role;
import com.edushop.entity.User;
import com.edushop.repository.UserRepository;
import com.edushop.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;

@Service
public class AuthService {

	private static final int BCRYPT_MAX_INPUT_BYTES = 72;

	private final AuthenticationManager authenticationManager;
	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	private final JwtTokenService jwtTokenService;

	public AuthService(
			AuthenticationManager authenticationManager,
			PasswordEncoder passwordEncoder,
			UserRepository userRepository,
			JwtTokenService jwtTokenService) {
		this.authenticationManager = authenticationManager;
		this.passwordEncoder = passwordEncoder;
		this.userRepository = userRepository;
		this.jwtTokenService = jwtTokenService;
	}

	@Transactional
	public AuthResponse register(RegistrationRequest request) {
		String email = normalizeEmail(request.getEmail());
		String rawPassword = request.getPassword();
		validatePasswordByteLength(rawPassword);

		if (userRepository.findByEmail(email).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		}

		String phone = request.getPhone() == null || request.getPhone().isBlank()
				? null
				: request.getPhone().trim();
		User user = new User(
				request.getFullName().trim(),
				email,
				passwordEncoder.encode(rawPassword),
				phone);

		try {
			User savedUser = userRepository.saveAndFlush(user);
			return toAuthResponse(savedUser);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "The account could not be created");
		}
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.getEmail());
		validatePasswordByteLength(request.getPassword());

		try {
			authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, request.getPassword()));
		} catch (BadCredentialsException | DisabledException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
		return toAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public UserProfileResponse getCurrentUser(long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
		List<String> roleNames = user.getRoles().stream()
				.map(Role::getName)
				.sorted()
				.toList();
		return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), roleNames, user.getPhone());
	}

	private AuthResponse toAuthResponse(User user) {
		JwtTokenService.IssuedAccessToken token = jwtTokenService.issue(user);
		return new AuthResponse(token.value(), token.expiresInSeconds());
	}

	@Transactional
	public UserProfileResponse updateProfile(long id, com.edushop.dto.auth.ProfileUpdateRequest request) {
		User user = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		user.updateProfile(request.fullName(), request.phone());
		return getCurrentUser(id);
	}

	@Transactional
	public void changePassword(long id, com.edushop.dto.auth.PasswordChangeRequest request) {
		validatePasswordByteLength(request.newPassword());
		User user = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash()))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		user.changePassword(passwordEncoder.encode(request.newPassword()));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private void validatePasswordByteLength(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds the supported byte length");
		}
	}
}
