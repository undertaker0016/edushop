package com.edushop.repository;

import com.edushop.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
	List<ClassSession> findByOffering_IdOrderByStartsAtAsc(Long offeringId);
	Optional<ClassSession> findByOffering_IdAndSessionNumber(Long offeringId, Short sessionNumber);
}
