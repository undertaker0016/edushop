package com.edushop.controller;

import com.edushop.dto.admin.AdminUserResponse;
import com.edushop.dto.admin.AdminAccountCreateRequest;
import com.edushop.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
	@PutMapping("/{id}")
	public AdminUserResponse update(@PathVariable long id,@Valid @RequestBody com.edushop.dto.auth.ProfileUpdateRequest request){
		return service.update(id,request);
	}
	private final AdminUserService service;
	public AdminUserController(AdminUserService service) { this.service = service; }

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an ADMIN or INSTRUCTOR account")
	public AdminUserResponse create(@Valid @RequestBody AdminAccountCreateRequest request) {
		return service.create(request);
	}

	@GetMapping @Operation(summary = "List accounts without exposing password hashes")
	public List<AdminUserResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@PatchMapping("/{id}/status") @Operation(summary = "Activate or ban an account")
	public AdminUserResponse setStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam @Pattern(regexp = "ACTIVE|BANNED") String status) {
		return service.setStatus(Long.parseLong(jwt.getSubject()), id, status);
	}
}
