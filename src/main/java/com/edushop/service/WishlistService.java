package com.edushop.service;

import com.edushop.dto.wishlist.WishlistItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class WishlistService {
	private final JdbcTemplate jdbc;

	public WishlistService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<WishlistItemResponse> list(long userId) {
		return jdbc.query(
				"""
							SELECT w.course_id, c.slug, c.title, c.thumbnail_url, w.added_at FROM wishlists w
						JOIN courses c ON c.id = w.course_id WHERE w.user_id = ? AND c.status = 'PUBLISHED' ORDER BY w.added_at DESC
						""",
				(rs, row) -> new WishlistItemResponse(rs.getLong(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getTimestamp(5).toLocalDateTime()),
				userId);
	}

	@Transactional
	public void add(long userId, long courseId) {
		Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE id = ? AND status = 'PUBLISHED'",
				Integer.class, courseId);
		if (found == null || found == 0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published course not found");
		jdbc.update("INSERT IGNORE INTO wishlists (user_id, course_id) VALUES (?, ?)", userId, courseId);
	}

	@Transactional
	public void remove(long userId, long courseId) {
		jdbc.update("DELETE FROM wishlists WHERE user_id = ? AND course_id = ?", userId, courseId);
	}
}
