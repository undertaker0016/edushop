package com.edushop.controller;

import com.edushop.dto.catalog.CategoryResponse;
import com.edushop.dto.catalog.CourseDetailResponse;
import com.edushop.dto.catalog.CourseSummaryResponse;
import com.edushop.dto.catalog.TagResponse;
import com.edushop.dto.catalog.OfferingResponse;
import com.edushop.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/categories")
	@Tag(name = "Categories")
	@Operation(summary = "List active course categories", description = "Public endpoint; inactive categories are omitted.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Active categories returned")
	})
	public List<CategoryResponse> getPublicCategories() {
		return catalogService.getPublicCategories();
	}

	@GetMapping("/courses")
	@Tag(name = "Courses")
	@Operation(summary = "List published courses", description = "Public, paginated catalog. Draft and archived courses are omitted.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Published courses returned"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size")
	})
	public CourseSummaryResponse.Page getPublishedCourses(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String tag) {
		return catalogService.getPublishedCourses(page, size, q, category, tag);
	}

	@GetMapping("/courses/{slug}")
	@Tag(name = "Courses")
	@Operation(summary = "Get a published course", description = "Draft and archived courses are not visible through this public endpoint.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Published course returned"),
			@ApiResponse(responseCode = "404", description = "Course not found or not published")
	})
	public CourseDetailResponse getPublishedCourse(@PathVariable String slug) {
		return catalogService.getPublishedCourse(slug);
	}

	@GetMapping("/tags")
	@Tag(name = "Courses")
	@Operation(summary = "List tags used by published courses", description = "Public tags are returned only when at least one published course uses them.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Public tags returned")
	})
	public List<TagResponse> getPublicTags() {
		return catalogService.getPublicTags();
	}

	@GetMapping("/courses/{slug}/offerings")
	@Tag(name = "Courses")
	@Operation(summary = "List open offerings for a published course", description = "Returns public schedules and VND prices; meeting credentials are never included.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Open offerings returned"),
			@ApiResponse(responseCode = "404", description = "Course not found or not published")
	})
	public List<OfferingResponse> getOpenOfferings(@PathVariable String slug) {
		return catalogService.getOpenOfferings(slug);
	}
}
