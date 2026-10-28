package com.edushop.controller;

import com.edushop.dto.admin.*;
import com.edushop.dto.catalog.CategoryResponse;
import com.edushop.dto.catalog.TagResponse;
import com.edushop.service.AdminCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminCatalogController {
	private final AdminCatalogService service;
	public AdminCatalogController(AdminCatalogService service) { this.service = service; }

	@GetMapping("/categories") @Operation(summary = "List active categories for administration")
	public List<CategoryResponse> categories() { return service.categories(); }
	@PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Create a course category")
	public CategoryResponse createCategory(@Valid @RequestBody CategoryWriteRequest request) { return service.createCategory(request); }
	@PutMapping("/categories/{slug}") @Operation(summary = "Update a category")
	public CategoryResponse updateCategory(@PathVariable String slug, @Valid @RequestBody CategoryWriteRequest request) {
		return service.updateCategory(slug, request);
	}
	@PatchMapping("/categories/{slug}/active") @Operation(summary = "Activate or deactivate a category")
	public ResponseEntity<Void> activeCategory(@PathVariable String slug, @RequestParam boolean active) {
		service.setCategoryActive(slug, active); return ResponseEntity.noContent().build();
	}
	@GetMapping("/tags") @Operation(summary = "List tags for administration")
	public List<TagResponse> tags() { return service.tags(); }
	@PostMapping("/tags") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Create a tag")
	public TagResponse createTag(@Valid @RequestBody TagWriteRequest request) { return service.createTag(request); }

	@PutMapping("/tags/{name}")
	public TagResponse updateTag(@PathVariable String name, @Valid @RequestBody TagWriteRequest request) {
		return service.updateTag(name, request);
	}
	@DeleteMapping("/tags/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTag(@PathVariable String name) { service.deleteTag(name); }
}
