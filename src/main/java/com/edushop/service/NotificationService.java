package com.edushop.service;

import com.edushop.dto.notifications.NotificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class NotificationService {
	private final JdbcTemplate jdbc;

	public NotificationService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> list(long userId, int page, int size) {
		if (page < 0 || size < 1 || size > 100)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
		return jdbc.query(
				"SELECT id,title,body,type,is_read,created_at,read_at FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
				(rs, row) -> new NotificationResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getBoolean(5), rs.getTimestamp(6).toLocalDateTime(),
						rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toLocalDateTime()),
				userId, size, (long) page * size);
	}

	@Transactional
	public void markRead(long userId, long notificationId) {
		int updated = jdbc.update(
				"UPDATE notifications SET is_read=TRUE, read_at=COALESCE(read_at,CURRENT_TIMESTAMP(6)) WHERE id=? AND user_id=?",
				notificationId, userId);
		if (updated == 0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
	}

	@Transactional
	public void markAllRead(long userId) {
		jdbc.update("UPDATE notifications SET is_read=TRUE,read_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND is_read=FALSE", userId);
	}
	@Transactional(readOnly=true)
	public long unreadCount(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=FALSE", Long.class, userId);
	}
}
