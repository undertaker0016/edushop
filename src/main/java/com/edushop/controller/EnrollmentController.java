package com.edushop.controller;

import com.edushop.dto.enrollment.*;
import com.edushop.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments/Classroom")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController {
	private final EnrollmentService service;
	public EnrollmentController(EnrollmentService service) { this.service = service; }

	@GetMapping @Operation(summary = "List the authenticated user's enrollment history")
	public List<EnrollmentResponse> list(@AuthenticationPrincipal Jwt jwt) { return service.list(userId(jwt)); }

	@GetMapping("/{enrollmentId}/sessions")
	@Operation(summary = "Get private class links after verifying ownership, paid order and active enrollment")
	public List<ClassroomSessionResponse> sessions(@AuthenticationPrincipal Jwt jwt, @PathVariable long enrollmentId) {
		return service.sessions(userId(jwt), enrollmentId);
	}

	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
