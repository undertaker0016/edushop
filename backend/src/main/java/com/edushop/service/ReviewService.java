package com.edushop.service;

import com.edushop.dto.reviews.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ReviewService {
	private final JdbcTemplate jdbc;

	public ReviewService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public java.util.Map<String,Object> own(long userId,long courseId) {
		var rows=jdbc.queryForList("SELECT r.id,r.rating,r.comment,r.created_at,r.updated_at,c.slug AS courseSlug FROM reviews r JOIN courses c ON c.id=r.course_id WHERE r.user_id=? AND r.course_id=?",userId,courseId);
		if(rows.isEmpty()) return java.util.Map.of("exists",false);
		var row=rows.getFirst();
		return java.util.Map.of("exists",true,"id",row.get("id"),"rating",row.get("rating"),"comment",row.get("comment")==null?"":row.get("comment"),"createdAt",row.get("created_at"),"updatedAt",row.get("updated_at"),"courseSlug",row.get("courseSlug"));
	}

	@Transactional(readOnly = true)
	public List<ReviewResponse> listPublic(String slug) {
		return jdbc.query("""
					SELECT r.id, u.full_name, r.rating, r.comment, r.created_at
				FROM reviews r JOIN users u ON u.id = r.user_id JOIN courses c ON c.id = r.course_id
				WHERE c.slug = ? AND c.status = 'PUBLISHED' AND r.is_hidden=FALSE ORDER BY r.created_at DESC
				""", (rs, row) -> new ReviewResponse(rs.getLong(1), rs.getString(2), rs.getInt(3),
				rs.getString(4), rs.getTimestamp(5).toLocalDateTime()), slug);
	}

	@Transactional
	public ReviewResponse upsert(long userId, long courseId, ReviewRequest request) {
		Integer eligible = jdbc.queryForObject("""
					SELECT COUNT(*) FROM courses c JOIN course_offerings o ON o.course_id = c.id
				JOIN enrollments e ON e.offering_id = o.id JOIN order_items oi ON oi.id = e.order_item_id
				JOIN orders ord ON ord.id = oi.order_id
				WHERE c.id = ? AND e.user_id = ? AND e.status = 'ACTIVE' AND ord.status = 'PAID'
				""", Integer.class, courseId, userId);
		if (eligible == null || eligible == 0)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"An active paid enrollment is required to review this course");
		jdbc.update(
				"""
						INSERT INTO reviews (user_id, course_id, rating, comment) VALUES (?, ?, ?, ?) AS incoming
					ON DUPLICATE KEY UPDATE rating = incoming.rating, comment = incoming.comment, updated_at = CURRENT_TIMESTAMP(6)
						""",
				userId, courseId, request.rating(), request.comment());
		return jdbc.queryForObject("""
					SELECT r.id, u.full_name, r.rating, r.comment, r.created_at FROM reviews r
					JOIN users u ON u.id = r.user_id WHERE r.user_id = ? AND r.course_id = ?
				""", (rs, row) -> new ReviewResponse(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4),
				rs.getTimestamp(5).toLocalDateTime()), userId, courseId);
	}

	@Transactional
	public void deleteOwn(long userId, long courseId) {
		if (jdbc.update("DELETE FROM reviews WHERE user_id=? AND course_id=?", userId, courseId) == 0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
	}
}
