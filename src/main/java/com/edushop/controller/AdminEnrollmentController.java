package com.edushop.controller;

import com.edushop.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.edushop.dto.enrollment.EnrollmentResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/enrollments")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminEnrollmentController {
	private final EnrollmentService service;
	public AdminEnrollmentController(EnrollmentService service) { this.service = service; }

	@GetMapping
	@Operation(summary = "List enrollments across all learners")
	public List<EnrollmentResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status) {
		return service.listForAdmin(page, size, status);
	}

	@PostMapping("/{id}/cancel") @Operation(summary = "Cancel access while keeping the enrollment record")
	public ResponseEntity<Void> cancel(@PathVariable long id) {
		service.cancelByAdmin(id);
		return ResponseEntity.noContent().build();
	}
}
