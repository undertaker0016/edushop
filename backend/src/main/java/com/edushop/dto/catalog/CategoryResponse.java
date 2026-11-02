package com.edushop.dto.catalog;

public record CategoryResponse(
		String slug,
		String name,
		String icon,
		Integer sortOrder) {
}
