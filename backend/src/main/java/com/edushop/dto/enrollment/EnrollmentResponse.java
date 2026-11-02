package com.edushop.dto.enrollment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EnrollmentResponse(Long id, Long userId, String learnerName, String learnerEmail,
		Long offeringId, Long courseId, String courseTitle, String courseSlug, String offeringCode, LocalDate startDate, LocalDate endDate,
		String status, LocalDateTime enrolledAt) {}
