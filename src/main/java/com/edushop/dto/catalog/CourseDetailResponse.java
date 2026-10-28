package com.edushop.dto.catalog;

import java.util.List;

public record CourseDetailResponse(
		Long courseId,
		String slug,
		String title,
		String description,
		String thumbnailUrl,
		List<CategoryResponse> categories,
		List<String> tags) {
}
