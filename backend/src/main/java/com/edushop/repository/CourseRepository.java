package com.edushop.repository;

import com.edushop.entity.Course;
import com.edushop.entity.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

	Page<Course> findByStatusOrderByTitleAsc(CourseStatus status, Pageable pageable);

	@EntityGraph(attributePaths = {"categories", "tags"})
	Optional<Course> findBySlugAndStatus(String slug, CourseStatus status);

	List<Course> findByCreatedByUserIdOrderByUpdatedAtDesc(Long createdByUserId);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);

	@Query(value = """
		select distinct c from Course c
		left join c.categories category left join c.tags tag
		where c.status = :status
		and (:search is null or lower(c.title) like lower(concat('%', :search, '%'))
		  or lower(c.slug) like lower(concat('%', :search, '%'))
		  or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
		and (:category is null or category.slug = :category)
		and (:tagName is null or lower(tag.name) = lower(:tagName))
		""",
		countQuery = """
		select count(distinct c.id) from Course c
		left join c.categories category left join c.tags tag
		where c.status = :status
		and (:search is null or lower(c.title) like lower(concat('%', :search, '%'))
		  or lower(c.slug) like lower(concat('%', :search, '%'))
		  or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
		and (:category is null or category.slug = :category)
		and (:tagName is null or lower(tag.name) = lower(:tagName))
		""")
	Page<Course> searchPublished(@Param("status") CourseStatus status, @Param("search") String search,
			@Param("category") String category, @Param("tagName") String tagName, Pageable pageable);

 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Course c where c.id=:id") Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
