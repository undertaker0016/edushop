package com.edushop.repository;

import com.edushop.entity.CourseOffering;
import com.edushop.entity.OfferingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
	@EntityGraph(attributePaths = "sessions")
	List<CourseOffering> findByCourseIdAndStatusOrderByStartDateAsc(Long courseId, OfferingStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from CourseOffering o where o.id = :id")
	Optional<CourseOffering> findByIdForUpdate(@Param("id") Long id);

	List<CourseOffering> findByCourseIdOrderByStartDateAsc(Long courseId);
}
