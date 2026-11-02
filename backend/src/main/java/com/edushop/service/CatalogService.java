package com.edushop.service;

import com.edushop.dto.catalog.CategoryResponse;
import com.edushop.dto.catalog.CourseDetailResponse;
import com.edushop.dto.catalog.CourseSummaryResponse;
import com.edushop.dto.catalog.TagResponse;
import com.edushop.dto.catalog.OfferingResponse;
import com.edushop.entity.CourseStatus;
import com.edushop.entity.OfferingStatus;
import com.edushop.repository.CategoryRepository;
import com.edushop.repository.CourseRepository;
import com.edushop.repository.TagRepository;
import com.edushop.repository.CourseOfferingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CatalogService {

	private final CategoryRepository categoryRepository;
	private final CourseRepository courseRepository;
	private final TagRepository tagRepository;
	private final CourseOfferingRepository offeringRepository;
	private final Clock clock;

	public CatalogService(
			CategoryRepository categoryRepository,
			CourseRepository courseRepository,
			TagRepository tagRepository,
			CourseOfferingRepository offeringRepository,
			Clock clock) {
		this.categoryRepository = categoryRepository;
		this.courseRepository = courseRepository;
		this.tagRepository = tagRepository;
		this.offeringRepository = offeringRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> getPublicCategories() {
		return categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
				.map(category -> new CategoryResponse(
						category.getSlug(),
						category.getName(),
						category.getIcon(),
						category.getSortOrder()))
				.toList();
	}

	@Transactional(readOnly = true)
	public CourseSummaryResponse.Page getPublishedCourses(int page, int size) {
		return getPublishedCourses(page, size, null, null, null);
	}

	@Transactional(readOnly = true)
	public CourseSummaryResponse.Page getPublishedCourses(int page, int size, String query, String category,
			String tag) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
		}
		if ((query != null && query.length() > 100) || (category != null && category.length() > 100)
				|| (tag != null && tag.length() > 50)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search filters are too long");
		}
		String normalizedQuery = normalize(query);
		String normalizedCategory = normalize(category);
		String normalizedTag = normalize(tag);

		Page<CourseSummaryResponse> result = courseRepository
				.searchPublished(CourseStatus.PUBLISHED, normalizedQuery, normalizedCategory, normalizedTag,
						PageRequest.of(page, size))
				.map(course -> new CourseSummaryResponse(
						course.getId(),
						course.getSlug(),
						course.getTitle(),
						course.getThumbnailUrl(),
						course.getDescription()));

		return new CourseSummaryResponse.Page(
				result.getContent(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank())
			return null;
		return value.trim();
	}

	@Transactional(readOnly = true)
	public CourseDetailResponse getPublishedCourse(String slug) {
		var course = courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

		List<CategoryResponse> categories = course.getCategories().stream()
				.filter(category -> category.isActive())
				.map(category -> new CategoryResponse(
						category.getSlug(),
						category.getName(),
						category.getIcon(),
						category.getSortOrder()))
				.sorted(java.util.Comparator.comparing(CategoryResponse::name))
				.toList();
		List<String> tags = course.getTags().stream()
				.map(tag -> tag.getName())
				.sorted()
				.toList();

		return new CourseDetailResponse(
				course.getId(),
				course.getSlug(),
				course.getTitle(),
				course.getDescription(),
				course.getThumbnailUrl(),
				categories,
				tags);
	}

	@Transactional(readOnly = true)
	public List<TagResponse> getPublicTags() {
		return tagRepository.findTagsUsedByPublishedCourses().stream()
				.map(tag -> new TagResponse(tag.getName()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OfferingResponse> getOpenOfferings(String courseSlug) {
		var course = courseRepository.findBySlugAndStatus(courseSlug, CourseStatus.PUBLISHED)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
		LocalDate today = LocalDate.now(clock);
		LocalDateTime now = LocalDateTime.now(clock);
		return offeringRepository.findByCourseIdAndStatusOrderByStartDateAsc(course.getId(), OfferingStatus.OPEN)
				.stream()
				.filter(offering -> !offering.getStartDate().isBefore(today)
						&& !offering.getEndDate().isBefore(today))
				.filter(offering -> {
					LocalDateTime closesAt = offering.getRegistrationClosesAt() == null
							? offering.getStartDate().atStartOfDay() : offering.getRegistrationClosesAt();
					return closesAt.isAfter(now);
				})
				.map(OfferingResponse::from).toList();
	}

    @Transactional(readOnly = true)
    public java.util.Map<String,Object> getCheckoutOffering(long id) {
        var offering = offeringRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Offering not found"));
        var course = courseRepository.findById(offering.getCourseId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var visible = getOpenOfferings(course.getSlug()).stream().filter(row -> row.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Offering not available"));
        return java.util.Map.of("offering",visible,"course",getPublishedCourse(course.getSlug()));
    }
}
