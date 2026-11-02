package com.edushop.repository;

import com.edushop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

	List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

	List<Category> findBySlugInAndActiveTrue(Collection<String> slugs);
}
