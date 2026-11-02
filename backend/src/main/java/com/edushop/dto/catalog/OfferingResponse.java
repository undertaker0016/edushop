package com.edushop.dto.catalog;

import com.edushop.entity.CourseOffering;
import com.edushop.entity.ClassSession;
import com.edushop.entity.OfferingStatus;
import com.edushop.entity.SessionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OfferingResponse(Long id, String code, String cohortName, LocalDate startDate,
		LocalDate endDate, LocalDateTime registrationClosesAt, String scheduleSummary,
		Short capacity, BigDecimal price, String currency, OfferingStatus status,
		List<SessionResponse> sessions) {
	public static OfferingResponse from(CourseOffering offering) {
		return new OfferingResponse(offering.getId(), offering.getOfferingCode(), offering.getCohortName(),
				offering.getStartDate(), offering.getEndDate(), offering.getRegistrationClosesAt(),
				offering.getScheduleSummary(), offering.getCapacity(), offering.getPrice(), offering.getCurrency(),
				offering.getStatus(), offering.getSessions().stream().map(SessionResponse::from).toList());
	}
	public record SessionResponse(Short number, String title, LocalDateTime startsAt,
			LocalDateTime endsAt, SessionStatus status) {
		static SessionResponse from(ClassSession session) {
			return new SessionResponse(session.getSessionNumber(), session.getTitle(), session.getStartsAt(),
					session.getEndsAt(), session.getStatus());
		}
	}
}
