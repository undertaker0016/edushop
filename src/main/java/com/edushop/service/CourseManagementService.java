package com.edushop.service;

import com.edushop.dto.catalog.CourseWriteRequest;
import com.edushop.dto.catalog.ManagedCourseResponse;
import com.edushop.entity.Course;
import com.edushop.entity.CourseStatus;
import com.edushop.repository.CourseRepository;
import com.edushop.repository.CategoryRepository;
import com.edushop.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CourseManagementService {
	private final CourseRepository courses;
	private final CategoryRepository categories;
	private final TagRepository tags;

	public CourseManagementService(CourseRepository courses, CategoryRepository categories, TagRepository tags) {
		this.courses = courses;
		this.categories = categories;
		this.tags = tags;
	}

	@Transactional
	public ManagedCourseResponse create(long userId, CourseWriteRequest request) {
		if (courses.existsBySlug(request.slug())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Course slug already exists");
		}
		Course course = new Course(userId, request.title(), request.slug(), request.description(),
				request.thumbnailUrl());
		applyTaxonomy(course, request.categorySlugs(), request.tags());
		return ManagedCourseResponse.from(courses.saveAndFlush(course));
	}

	@Transactional(readOnly = true)
	public List<ManagedCourseResponse> list(long userId, boolean admin) {
		List<Course> result = admin ? courses.findAll() : courses.findByCreatedByUserIdOrderByUpdatedAtDesc(userId);
		return result.stream().map(ManagedCourseResponse::from).toList();
	}

	@Transactional
	public ManagedCourseResponse update(long userId, boolean admin, long courseId, CourseWriteRequest request) {
		Course course = ownedCourse(userId, admin, courseId);
		if (courses.existsBySlugAndIdNot(request.slug(), courseId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Course slug already exists");
		}
		try {
			course.updateDraft(request.title(), request.slug(), request.description(), request.thumbnailUrl());
			applyTaxonomy(course, request.categorySlugs(), request.tags());
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
		}
		return ManagedCourseResponse.from(courses.saveAndFlush(course));
	}

	@Transactional
	public ManagedCourseResponse changeStatus(long userId, boolean admin, long courseId, boolean publish) {
		Course course = ownedCourse(userId, admin, courseId);
		if (publish) {
			if (course.getStatus() != CourseStatus.DRAFT) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft courses can be published");
			}
			if (course.getDescription() == null || course.getDescription().isBlank()
					|| course.getCategories().stream().noneMatch(com.edushop.entity.Category::isActive)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"A course needs a description and at least one active category before publishing");
			}
			course.publish();
		} else {
			if (course.getStatus() == CourseStatus.ARCHIVED) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Course is already archived");
			}
			course.archive();
		}
		return ManagedCourseResponse.from(courses.saveAndFlush(course));
	}

	private void applyTaxonomy(Course course, List<String> categorySlugs, List<String> tagNames) {
		List<String> slugs = categorySlugs == null ? List.of()
				: categorySlugs.stream().map(String::trim).distinct().toList();
		List<String> normalizedTags = tagNames == null ? List.of()
				: tagNames.stream().map(String::trim).distinct().toList();
		var foundCategories = slugs.isEmpty() ? List.<com.edushop.entity.Category>of()
				: categories.findBySlugInAndActiveTrue(slugs);
		if (foundCategories.size() != slugs.size())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more categories are missing or inactive");
		var foundTags = normalizedTags.isEmpty() ? List.<com.edushop.entity.Tag>of()
				: tags.findByNameIn(normalizedTags);
		if (foundTags.size() != normalizedTags.size())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more tags do not exist");
		course.replaceCategories(foundCategories);
		course.replaceTags(foundTags);
	}

	private Course ownedCourse(long userId, boolean admin, long courseId) {
		Course course = courses.findById(courseId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
		if (!admin && !course.getCreatedByUserId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
		}
		return course;
	}
	@Transactional(readOnly=true)
	public ManagedCourseResponse detail(long userId,boolean admin,long id){return ManagedCourseResponse.from(ownedCourse(userId,admin,id));}
}
