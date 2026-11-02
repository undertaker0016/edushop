package com.edushop.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInstructorRequest(@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 150) String fullName) {}
