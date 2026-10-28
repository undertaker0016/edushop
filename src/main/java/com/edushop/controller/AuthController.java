package com.edushop.controller;

import com.edushop.dto.auth.AuthResponse;
import com.edushop.dto.auth.LoginRequest;
import com.edushop.dto.auth.RegistrationRequest;
import com.edushop.dto.auth.UserProfileResponse;
import com.edushop.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register a buyer account without an application role")
	public AuthResponse register(@Valid @RequestBody RegistrationRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Authenticate with email and password")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/me")
	@Operation(summary = "Get the authenticated account profile", security = @SecurityRequirement(name = "bearerAuth"))
	public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
		return authService.getCurrentUser(Long.parseLong(jwt.getSubject()));
	}

	@org.springframework.web.bind.annotation.PutMapping("/me")
	@SecurityRequirement(name="bearerAuth")
	public UserProfileResponse update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody com.edushop.dto.auth.ProfileUpdateRequest request) {
		return authService.updateProfile(Long.parseLong(jwt.getSubject()), request);
	}

	@org.springframework.web.bind.annotation.PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name="bearerAuth")
	public void password(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody com.edushop.dto.auth.PasswordChangeRequest request) {
		authService.changePassword(Long.parseLong(jwt.getSubject()), request);
	}
}
