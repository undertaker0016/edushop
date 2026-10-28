package com.edushop.controller;

import com.edushop.dto.catalog.CourseWriteRequest;
import com.edushop.dto.catalog.ManagedCourseResponse;
import com.edushop.service.CourseManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor/courses")
@Tag(name = "Courses")
@SecurityRequirement(name = "bearerAuth")
public class InstructorCourseController {
	@GetMapping("/{courseId}")
	public ManagedCourseResponse detail(@AuthenticationPrincipal Jwt jwt, Authentication auth,@PathVariable long courseId){
		return courseService.detail(userId(jwt),isAdmin(auth),courseId);
	}
	private final CourseManagementService courseService;

	public InstructorCourseController(CourseManagementService courseService) {
		this.courseService = courseService;
	}

	@PostMapping
	@Operation(summary = "Create a draft course")
	public ManagedCourseResponse create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CourseWriteRequest request) {
		return courseService.create(userId(jwt), request);
	}

	@GetMapping
	@Operation(summary = "List your courses; administrators can list all courses")
	public List<ManagedCourseResponse> list(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
		return courseService.list(userId(jwt), isAdmin(authentication));
	}

	@PutMapping("/{courseId}")
	@Operation(summary = "Update a draft course")
	public ManagedCourseResponse update(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
			@PathVariable long courseId, @Valid @RequestBody CourseWriteRequest request) {
		return courseService.update(userId(jwt), isAdmin(authentication), courseId, request);
	}

	@PostMapping("/{courseId}/publish")
	@Operation(summary = "Publish a draft course")
	public ManagedCourseResponse publish(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
			@PathVariable long courseId) {
		return courseService.changeStatus(userId(jwt), isAdmin(authentication), courseId, true);
	}

	@PostMapping("/{courseId}/archive")
	@Operation(summary = "Archive a course")
	public ManagedCourseResponse archive(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
			@PathVariable long courseId) {
		return courseService.changeStatus(userId(jwt), isAdmin(authentication), courseId, false);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

	private static boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}
}
