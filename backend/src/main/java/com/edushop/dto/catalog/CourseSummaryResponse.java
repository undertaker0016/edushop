package com.edushop.dto.catalog;

import java.util.List;

public record CourseSummaryResponse(
		Long courseId,
		String slug,
		String title,
		String thumbnailUrl,
		String description) {

	public record Page(
			List<CourseSummaryResponse> items,
			int page,
			int size,
			long totalElements,
			int totalPages) {
	}
}
