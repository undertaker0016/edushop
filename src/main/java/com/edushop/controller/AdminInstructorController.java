package com.edushop.controller;

import com.edushop.dto.admin.CreateInstructorRequest;
import com.edushop.dto.admin.InstructorResponse;
import com.edushop.service.AdminInstructorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/instructors")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminInstructorController {
	private final AdminInstructorService service;
	public AdminInstructorController(AdminInstructorService service) { this.service = service; }

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Grant instructor access and create an instructor profile for an existing account")
	public InstructorResponse create(@Valid @RequestBody CreateInstructorRequest request) {
		return service.create(request);
	}

	@GetMapping("/{id}")
	public com.edushop.dto.instructor.InstructorProfileResponse get(@PathVariable long id){return service.get(id);}
	@PutMapping("/{id}")
	public com.edushop.dto.instructor.InstructorProfileResponse update(@PathVariable long id,
			@Valid @RequestBody com.edushop.dto.instructor.InstructorProfileRequest request){return service.update(id,request);}
	@DeleteMapping("/{id}/role") @ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@PathVariable long id){service.revoke(id);}
}
