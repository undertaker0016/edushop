package com.edushop.dto.enrollment;

import java.time.LocalDateTime;

public record ClassroomSessionResponse(Short sessionNumber, String title, LocalDateTime startsAt,
		LocalDateTime endsAt, String meetingProvider, String meetingUrl, String meetingPassword,
		String notes, String status) {}
