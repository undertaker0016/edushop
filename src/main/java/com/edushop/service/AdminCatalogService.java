package com.edushop.service;

import com.edushop.dto.admin.*;
import com.edushop.dto.catalog.CategoryResponse;
import com.edushop.dto.catalog.TagResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class AdminCatalogService {
	private final JdbcTemplate jdbc;

	public AdminCatalogService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> categories() {
		return jdbc.query(
				"SELECT slug,name,icon,sort_order FROM categories ORDER BY sort_order,name",
				(rs, row) -> new CategoryResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
	}

	@Transactional
	public CategoryResponse createCategory(CategoryWriteRequest request) {
		try {
			jdbc.update("INSERT INTO categories (name,slug,icon,sort_order,is_active) VALUES (?,?,?,?,TRUE)",
					request.name().trim(), request.slug().trim(), request.icon(), request.sortOrder());
		} catch (DataIntegrityViolationException ex) {
			throw conflict("Category slug already exists");
		}
		return category(request.slug());
	}

	@Transactional
	public CategoryResponse updateCategory(String slug, CategoryWriteRequest request) {
		try {
			int count = jdbc.update("UPDATE categories SET name=?,slug=?,icon=?,sort_order=? WHERE slug=?",
					request.name().trim(), request.slug().trim(), request.icon(), request.sortOrder(), slug);
			if (count == 0)
				throw notFound("Category not found");
		} catch (DataIntegrityViolationException ex) {
			throw conflict("Category slug already exists");
		}
		return category(request.slug());
	}

	@Transactional
	public void setCategoryActive(String slug, boolean active) {
		if (jdbc.update("UPDATE categories SET is_active=? WHERE slug=?", active, slug) == 0)
			throw notFound("Category not found");
	}

	@Transactional(readOnly = true)
	public List<TagResponse> tags() {
		return jdbc.query("SELECT name FROM tags ORDER BY name", (rs, row) -> new TagResponse(rs.getString(1)));
	}

	@Transactional
	public TagResponse createTag(TagWriteRequest request) {
		try {
			jdbc.update("INSERT INTO tags (name) VALUES (?)", request.name().trim());
		} catch (DataIntegrityViolationException ex) {
			throw conflict("Tag already exists");
		}
		return new TagResponse(request.name().trim());
	}

	private CategoryResponse category(String slug) {
		return jdbc.queryForObject("SELECT slug,name,icon,sort_order FROM categories WHERE slug=?",
				(rs, row) -> new CategoryResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)),
				slug);
	}

	@Transactional
	public TagResponse updateTag(String name, TagWriteRequest request) {
		if (jdbc.update("UPDATE tags SET name=? WHERE name=?", request.name().trim(), name) == 0)
			throw notFound("Tag not found");
		return new TagResponse(request.name().trim());
	}

	@Transactional
	public void deleteTag(String name) {
		var ids = jdbc.queryForList("SELECT id FROM tags WHERE name=? FOR UPDATE", Long.class, name);
		if (ids.isEmpty()) throw notFound("Tag not found");
		if (jdbc.queryForObject("SELECT COUNT(*) FROM course_tags WHERE tag_id=?", Long.class, ids.getFirst()) > 0)
			throw conflict("Tag is used by a course");
		jdbc.update("DELETE FROM tags WHERE id=?", ids.getFirst());
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}
}
