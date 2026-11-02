package com.edushop.repository;

import com.edushop.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Collection;

public interface TagRepository extends JpaRepository<Tag, Integer> {

	@Query(value = """
			SELECT DISTINCT t.*
			FROM tags t
			JOIN course_tags ct ON ct.tag_id = t.id
			JOIN courses c ON c.id = ct.course_id
			WHERE c.status = 'PUBLISHED'
			ORDER BY t.name
			""", nativeQuery = true)
	List<Tag> findTagsUsedByPublishedCourses();

	List<Tag> findByNameIn(Collection<String> names);
}
