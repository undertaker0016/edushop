package com.edushop.controller;

import com.edushop.dto.instructor.InstructorProfileRequest;
import com.edushop.dto.instructor.InstructorProfileResponse;
import com.edushop.service.InstructorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instructor/profile")
@Tag(name = "Instructor and Offerings")
@SecurityRequirement(name = "bearerAuth")
public class InstructorProfileController {
	private final InstructorProfileService service;

	public InstructorProfileController(InstructorProfileService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "Get your instructor profile")
	public InstructorProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
		return service.get(userId(jwt));
	}

	@PutMapping
	@Operation(summary = "Update your instructor profile")
	public InstructorProfileResponse update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody InstructorProfileRequest request) {
		return service.update(userId(jwt), request);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}
}
