package com.edushop.controller;

import com.edushop.dto.notifications.NotificationResponse;
import com.edushop.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
	private final NotificationService service;
	public NotificationController(NotificationService service) { this.service = service; }

	@GetMapping @Operation(summary = "List your notifications")
	public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(userId(jwt), page, size);
	}
	@PatchMapping("/{id}/read") @Operation(summary = "Mark your notification as read")
	public ResponseEntity<Void> read(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.markRead(userId(jwt), id); return ResponseEntity.noContent().build();
	}
	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
	@PatchMapping("/read-all")
	public ResponseEntity<Void> readAll(@AuthenticationPrincipal Jwt jwt) {
		service.markAllRead(userId(jwt)); return ResponseEntity.noContent().build();
	}
	@GetMapping("/unread-count")
	public java.util.Map<String,Long> count(@AuthenticationPrincipal Jwt jwt) {
		return java.util.Map.of("count", service.unreadCount(userId(jwt)));
	}
}
