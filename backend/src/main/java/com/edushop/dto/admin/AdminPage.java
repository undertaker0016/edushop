package com.edushop.dto.admin;
import java.util.List;
import java.util.Map;
/** Explicit SQL projections only: never entities or password hashes. */
public record AdminPage(List<Map<String,Object>> items, int page, int size, long totalElements, long totalPages) {}

