package com.edushop.dto.catalog;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CourseWriteRequest(
		@NotBlank @Size(max = 255) String title,
		@NotBlank @Size(max = 255)
		@Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "slug must be lowercase words separated by hyphens")
		String slug,
		@Size(max = 10000) String description,
		@Size(max = 500) String thumbnailUrl,
		@Size(max = 20) List<@NotBlank @Size(max = 100) String> categorySlugs,
		@Size(max = 20) List<@NotBlank @Size(max = 50) String> tags) {
}
