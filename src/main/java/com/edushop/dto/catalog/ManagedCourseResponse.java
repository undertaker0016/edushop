package com.edushop.dto.catalog;

import com.edushop.entity.Course;
import com.edushop.entity.CourseStatus;

import java.time.LocalDateTime;

public record ManagedCourseResponse(
		Long id,
		String title,
		String slug,
		String description,
		String thumbnailUrl,
		CourseStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
	public static ManagedCourseResponse from(Course course) {
		return new ManagedCourseResponse(course.getId(), course.getTitle(), course.getSlug(),
				course.getDescription(), course.getThumbnailUrl(), course.getStatus(),
				course.getCreatedAt(), course.getUpdatedAt());
	}
}
