package com.edushop.controller;

import com.edushop.dto.reviews.*;
import com.edushop.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews")
public class ReviewController {
	private final ReviewService service;
	public ReviewController(ReviewService service) { this.service = service; }

	@GetMapping("/courses/{slug}") @Operation(summary = "List reviews for a published course")
	public List<ReviewResponse> list(@PathVariable String slug) { return service.listPublic(slug); }

	@PutMapping("/courses/{courseId}")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Create or update your review; requires an active paid enrollment")
	public ReviewResponse upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable long courseId,
			@Valid @RequestBody ReviewRequest request) {
		return service.upsert(Long.parseLong(jwt.getSubject()), courseId, request);
	}

	@DeleteMapping("/courses/{courseId}")
	@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	@SecurityRequirement(name="bearerAuth")
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long courseId) {
		service.deleteOwn(Long.parseLong(jwt.getSubject()), courseId);
	}
}
