package com.edushop.dto.admin;

import jakarta.validation.constraints.*;

public record CategoryWriteRequest(@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
		@Size(max = 30) String icon,
		@NotNull @Min(0) Integer sortOrder) {}
