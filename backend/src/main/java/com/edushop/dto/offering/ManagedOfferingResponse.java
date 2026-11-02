package com.edushop.dto.offering;

import com.edushop.entity.CourseOffering;
import com.edushop.entity.OfferingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ManagedOfferingResponse(Long id, Long courseId, Long instructorId, String code,
		String cohortName, LocalDate startDate, LocalDate endDate, LocalDateTime registrationClosesAt,
		String scheduleSummary, Short capacity, BigDecimal price, String currency, OfferingStatus status) {
	public static ManagedOfferingResponse from(CourseOffering o) {
		return new ManagedOfferingResponse(o.getId(), o.getCourseId(), o.getInstructorId(), o.getOfferingCode(),
				o.getCohortName(), o.getStartDate(), o.getEndDate(), o.getRegistrationClosesAt(),
				o.getScheduleSummary(), o.getCapacity(), o.getPrice(), o.getCurrency(), o.getStatus());
	}
}
