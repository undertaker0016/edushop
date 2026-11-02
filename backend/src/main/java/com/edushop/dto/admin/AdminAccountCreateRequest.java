package com.edushop.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequest(
		@NotBlank @Size(max = 100) String fullName,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 12, max = 72) String password,
		@Size(max = 20) String phone,
		@NotBlank @Pattern(regexp = "ADMIN|INSTRUCTOR") String role) {
}
