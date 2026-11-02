package com.edushop.dto.admin;

public record AdminCategoryResponse(Long id, String slug, String name, String icon, Integer sortOrder, boolean active) {}
