package com.edushop.controller;

import com.edushop.dto.wishlist.WishlistItemResponse;
import com.edushop.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlists")
@Tag(name = "Wishlists")
@SecurityRequirement(name = "bearerAuth")
public class WishlistController {
	private final WishlistService service;
	public WishlistController(WishlistService service) { this.service = service; }

	@GetMapping @Operation(summary = "List your own wishlist")
	public List<WishlistItemResponse> list(@AuthenticationPrincipal Jwt jwt) { return service.list(userId(jwt)); }
	@PostMapping("/courses/{courseId}") @Operation(summary = "Add a published course to your wishlist")
	public ResponseEntity<Void> add(@AuthenticationPrincipal Jwt jwt, @PathVariable long courseId) {
		service.add(userId(jwt), courseId); return ResponseEntity.noContent().build();
	}
	@DeleteMapping("/courses/{courseId}") @Operation(summary = "Remove a course from your wishlist")
	public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long courseId) {
		service.remove(userId(jwt), courseId); return ResponseEntity.noContent().build();
	}
	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
