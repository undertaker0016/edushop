package com.edushop.dto.offering;

import com.edushop.entity.ClassSession;
import com.edushop.entity.MeetingProvider;
import com.edushop.entity.SessionStatus;
import java.time.LocalDateTime;

public record ManagedSessionResponse(Long id, Short sessionNumber, String title,
		LocalDateTime startsAt, LocalDateTime endsAt, MeetingProvider meetingProvider,
		String meetingUrl, String meetingPassword, String notes, SessionStatus status) {
	public static ManagedSessionResponse from(ClassSession s) {
		return new ManagedSessionResponse(s.getId(), s.getSessionNumber(), s.getTitle(), s.getStartsAt(),
				s.getEndsAt(), s.getMeetingProvider(), s.getMeetingUrl(), s.getMeetingPassword(), s.getNotes(), s.getStatus());
	}
}
