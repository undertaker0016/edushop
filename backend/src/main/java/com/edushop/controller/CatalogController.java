package com.edushop.controller;

import com.edushop.dto.catalog.CategoryResponse;
import com.edushop.dto.catalog.CourseDetailResponse;
import com.edushop.dto.catalog.OfferingResponse;
import com.edushop.dto.catalog.TagResponse;
import com.edushop.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

  private final CatalogService catalogService;
  private final com.edushop.service.CatalogDiscoveryService discovery;

  public CatalogController(
      CatalogService catalogService, com.edushop.service.CatalogDiscoveryService discovery) {
    this.discovery = discovery;
    this.catalogService = catalogService;
  }

  @GetMapping("/categories")
  @Tag(name = "Categories")
  @Operation(
      summary = "List active course categories",
      description = "Public endpoint; inactive categories are omitted.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Active categories returned")})
  public List<CategoryResponse> getPublicCategories() {
    return catalogService.getPublicCategories();
  }

  @GetMapping("/courses")
  @Tag(name = "Courses")
  @Operation(
      summary = "List published courses",
      description = "Public, paginated catalog. Draft and archived courses are omitted.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Published courses returned"),
    @ApiResponse(responseCode = "400", description = "Invalid page or size")
  })
  public java.util.Map<String, Object> getPublishedCourses(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(required = false) java.math.BigDecimal minPrice,
      @RequestParam(required = false) java.math.BigDecimal maxPrice,
      @RequestParam(required = false) Double minRating) {
    return discovery.search(page, size, q, category, tag, sort, minPrice, maxPrice, minRating);
  }

  @GetMapping("/courses/{slug}")
  @Tag(name = "Courses")
  @Operation(
      summary = "Get a published course",
      description = "Draft and archived courses are not visible through this public endpoint.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Published course returned"),
    @ApiResponse(responseCode = "404", description = "Course not found or not published")
  })
  public CourseDetailResponse getPublishedCourse(@PathVariable String slug) {
    return catalogService.getPublishedCourse(slug);
  }

  @GetMapping("/tags")
  @Tag(name = "Courses")
  @Operation(
      summary = "List tags used by published courses",
      description = "Public tags are returned only when at least one published course uses them.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Public tags returned")})
  public List<TagResponse> getPublicTags() {
    return catalogService.getPublicTags();
  }

  @GetMapping("/courses/{slug}/offerings")
  @Tag(name = "Courses")
  @Operation(
      summary = "List open offerings for a published course",
      description =
          "Returns public schedules and VND prices; meeting credentials are never included.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Open offerings returned"),
    @ApiResponse(responseCode = "404", description = "Course not found or not published")
  })
  public List<OfferingResponse> getOpenOfferings(@PathVariable String slug) {
    return catalogService.getOpenOfferings(slug);
  }

  @GetMapping("/offerings/{id}")
  @Operation(summary = "Get an open published offering for checkout; excludes meeting credentials")
  public java.util.Map<String, Object> checkoutOffering(@PathVariable long id) {
    return catalogService.getCheckoutOffering(id);
  }

  @GetMapping("/courses/suggestions")
  public Object suggestions(@RequestParam(defaultValue = "") String q) {
    if (q.isBlank()) return java.util.List.of();
    return discovery.search(0, 6, q, null, null, "newest", null, null, null).get("items");
  }

  @GetMapping("/courses/{slug}/metrics")
  public Object metrics(@PathVariable String slug) {
    return discovery.metrics(slug);
  }
}
