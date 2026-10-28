package com.edushop.dto.notifications;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String title, String body, String type,
		boolean read, LocalDateTime createdAt, LocalDateTime readAt) {}
