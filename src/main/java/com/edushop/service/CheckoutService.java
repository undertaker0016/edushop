package com.edushop.service;

import com.edushop.dto.orders.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.*;

@Service
public class CheckoutService {
	private static final BigDecimal ZERO = new BigDecimal("0.00");
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public CheckoutService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public OrderResponse checkout(long userId, String idempotencyKey, CheckoutRequest request) {
		validateIdempotencyKey(idempotencyKey);
		List<Long> requestedIds = request.offeringIds();
		if (requestedIds.stream().distinct().count() != requestedIds.size())
			throw badRequest("Offering IDs must be unique");
		List<Long> sortedIds = requestedIds.stream().sorted().toList();
		String couponCode = request.couponCode() == null || request.couponCode().isBlank()
				? null : request.couponCode().trim().toUpperCase(Locale.ROOT);
		String fingerprint = fingerprint(sortedIds, couponCode);

		jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
		expireStaleOrders();
		List<Map<String, Object>> prior = jdbc.queryForList(
				"SELECT id, request_fingerprint FROM orders WHERE user_id = ? AND idempotency_key = ?",
				userId, idempotencyKey);
		if (!prior.isEmpty()) {
			if (!fingerprint.equals(prior.getFirst().get("request_fingerprint")))
				throw conflict("Idempotency key was already used for a different checkout request");
			return orderResponse(((Number) prior.getFirst().get("id")).longValue());
		}

		List<OfferingData> ordered = new ArrayList<>();
		for (Long id : sortedIds) {
			List<OfferingData> matches = jdbc.query("""
					SELECT o.id, o.offering_code, o.cohort_name, o.start_date, o.end_date,
					       o.registration_closes_at, o.capacity, o.price, o.status, c.title, c.status AS course_status
				FROM course_offerings o JOIN courses c ON c.id = o.course_id
				WHERE o.id = ? FOR UPDATE
				""", (rs, row) -> new OfferingData(rs.getLong("id"), rs.getString("offering_code"),
					rs.getString("cohort_name"), rs.getDate("start_date").toLocalDate(),
					rs.getDate("end_date").toLocalDate(),
					rs.getTimestamp("registration_closes_at") == null ? null : rs.getTimestamp("registration_closes_at").toLocalDateTime(),
					rs.getShort("capacity"), rs.getBigDecimal("price"), rs.getString("status"), rs.getString("title"),
					rs.getString("course_status")), id);
			if (matches.isEmpty()) throw notFound("Offering not found");
			ordered.add(matches.getFirst());
		}
		LocalDate today = LocalDate.now(clock);
		LocalDateTime now = LocalDateTime.now(clock);
		for (OfferingData offering : ordered) {
			if (!"PUBLISHED".equals(offering.courseStatus)) throw conflict("Course is not available for purchase");
			if (!"OPEN".equals(offering.status)) throw conflict("Offering is not open for sale");
			if (offering.startDate.isBefore(today) || offering.endDate.isBefore(today)) throw conflict("Offering dates have passed");
			LocalDateTime closeAt = offering.registrationClosesAt != null
					? offering.registrationClosesAt : offering.startDate.atStartOfDay();
			if (!closeAt.isAfter(now)) throw conflict("Registration has closed for this offering");
			if (activeSeats(offering.id) >= offering.capacity) throw conflict("Offering is full");
			Integer enrolled = jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ? AND offering_id = ? AND status = 'ACTIVE'",
					Integer.class, userId, offering.id);
			if (enrolled != null && enrolled > 0) throw conflict("You are already enrolled in this offering");
			Integer reservedByUser = jdbc.queryForObject("""
					SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id
					LEFT JOIN enrollments e ON e.order_item_id = oi.id
					WHERE oi.offering_id = ? AND o.user_id = ? AND (
					 (o.status = 'PENDING' AND o.expires_at > CURRENT_TIMESTAMP(6))
					 OR (o.status = 'PAID' AND (e.id IS NULL OR e.status <> 'CANCELLED')))
					""", Integer.class, offering.id, userId);
			if (reservedByUser != null && reservedByUser > 0) throw conflict("You already purchased or reserved this offering");
		}

		BigDecimal subtotal = ordered.stream().map(OfferingData::price).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		CouponData coupon = couponCode == null ? null : reserveCoupon(userId, couponCode, subtotal, now);
		BigDecimal discount = coupon == null ? ZERO : discount(coupon, subtotal);
		BigDecimal total = subtotal.subtract(discount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
		boolean free = total.signum() == 0;
		LocalDateTime expiresAt = free ? null : now.plusMinutes(15);
		String orderNumber = "EDU-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO orders (order_number, user_id, coupon_id, subtotal, discount_amount, total_amount,
					 currency, status, paid_at, expires_at, idempotency_key, request_fingerprint)
				VALUES (?, ?, ?, ?, ?, ?, 'VND', ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, orderNumber);
			ps.setLong(2, userId);
			if (coupon == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, coupon.id);
			ps.setBigDecimal(4, subtotal); ps.setBigDecimal(5, discount); ps.setBigDecimal(6, total);
			ps.setString(7, free ? "PAID" : "PENDING");
			if (free) ps.setObject(8, now); else ps.setNull(8, java.sql.Types.TIMESTAMP);
			if (expiresAt == null) ps.setNull(9, java.sql.Types.TIMESTAMP); else ps.setObject(9, expiresAt);
			ps.setString(10, idempotencyKey); ps.setString(11, fingerprint);
			return ps;
		}, keyHolder);
		long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();
		for (OfferingData offering : ordered) {
			String title = offering.cohortName == null || offering.cohortName.isBlank()
					? offering.courseTitle : offering.courseTitle + " - " + offering.cohortName;
			KeyHolder itemKey = new GeneratedKeyHolder();
			jdbc.update(connection -> {
				PreparedStatement ps = connection.prepareStatement(
						"INSERT INTO order_items (order_id, offering_id, offering_title_snapshot, unit_price) VALUES (?, ?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS);
				ps.setLong(1, orderId); ps.setLong(2, offering.id); ps.setString(3, title.length()>255?title.substring(0,255):title); ps.setBigDecimal(4, offering.price);
				return ps;
			}, itemKey);
			if (free) {
				jdbc.update("INSERT INTO enrollments (user_id, offering_id, order_item_id, status) VALUES (?, ?, ?, 'ACTIVE')",
						userId, offering.id, Objects.requireNonNull(itemKey.getKey()).longValue());
				jdbc.update("INSERT INTO notifications (user_id,title,body,type) VALUES (?,?,?,'ENROLLMENT_CREATED')",
						userId, "Ghi danh thành công", "Bạn đã được ghi danh vào lớp " + title + ".");
			}
		}
		if (coupon != null) {
			jdbc.update("INSERT INTO coupon_redemptions (coupon_id, user_id, order_id, status) VALUES (?, ?, ?, ?)",
					coupon.id, userId, orderId, free ? "CONSUMED" : "RESERVED");
		}
		return orderResponse(orderId);
	}

	@Transactional
	public OrderResponse get(long userId, boolean admin, long orderId) {
		expireStaleOrders();
		List<Long> owners = jdbc.query("SELECT user_id FROM orders WHERE id = ?", (rs, row) -> rs.getLong(1), orderId);
		if (owners.isEmpty()) throw notFound("Order not found");
		if (!admin && owners.getFirst() != userId) throw notFound("Order not found");
		return orderResponse(orderId);
	}

	@Transactional
	public List<OrderResponse> list(long userId, boolean admin, int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw badRequest("Invalid page or size");
		expireStaleOrders();
		long offset = (long) page * size;
		List<Long> ids = admin
				? jdbc.query("SELECT id FROM orders ORDER BY created_at DESC LIMIT ? OFFSET ?", (rs, row) -> rs.getLong(1), size, offset)
				: jdbc.query("SELECT id FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", (rs, row) -> rs.getLong(1), userId, size, offset);
		return ids.stream().map(this::orderResponse).toList();
	}

	@Transactional
	public void cancel(long userId, boolean admin, long orderId) {
		List<OrderOwner> found = jdbc.query(
				"SELECT user_id, status FROM orders WHERE id = ? FOR UPDATE",
				(rs, row) -> new OrderOwner(rs.getLong("user_id"), rs.getString("status")), orderId);
		if (found.isEmpty() || (!admin && found.getFirst().userId != userId)) {
			throw notFound("Order not found");
		}
		if (!"PENDING".equals(found.getFirst().status)) {
			throw conflict("Only pending orders can be cancelled");
		}
		jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'", orderId);
		jdbc.update("UPDATE payments SET status = 'CANCELLED' WHERE order_id = ? AND status = 'PENDING'", orderId);
		releaseReservedCoupon(orderId);
	}

	private CouponData reserveCoupon(long userId, String code, BigDecimal subtotal, LocalDateTime now) {
		List<CouponData> found = jdbc.query("""
				SELECT id, code, discount_type, discount_value, max_discount_amount, max_uses,
				       used_count, max_uses_per_user, expires_at, is_active
			FROM coupons WHERE code = ? FOR UPDATE
			""", (rs, row) -> new CouponData(rs.getLong("id"), rs.getString("code"),
				rs.getString("discount_type"), rs.getBigDecimal("discount_value"), rs.getBigDecimal("max_discount_amount"),
				nullableLong(rs.getObject("max_uses")), rs.getLong("used_count"),
				nullableLong(rs.getObject("max_uses_per_user")),
				rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime(),
				rs.getBoolean("is_active")), code);
		if (found.isEmpty()) throw conflict("Coupon is invalid");
		CouponData coupon = found.getFirst();
		if (!coupon.active || (coupon.expiresAt != null && !coupon.expiresAt.isAfter(now))) throw conflict("Coupon is expired or inactive");
		BigDecimal discount = discount(coupon, subtotal);
		if (discount.signum() == 0) throw conflict("Coupon does not apply to this order");
		if (coupon.maxUses != null && coupon.usedCount >= coupon.maxUses) throw conflict("Coupon usage limit has been reached");
		if (coupon.maxUsesPerUser != null) {
		Long used = jdbc.queryForObject("""
					SELECT COUNT(*) FROM coupon_redemptions cr JOIN orders o ON o.id = cr.order_id
					WHERE cr.coupon_id = ? AND cr.user_id = ? AND (
					 cr.status = 'CONSUMED' OR (cr.status = 'RESERVED' AND o.status = 'PENDING'
					 AND (o.expires_at IS NULL OR o.expires_at > CURRENT_TIMESTAMP(6))))
					""", Long.class, coupon.id, userId);
			if (used != null && used >= coupon.maxUsesPerUser) throw conflict("Coupon per-user usage limit has been reached");
		}
		int changed = jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ? AND (max_uses IS NULL OR used_count < max_uses)", coupon.id);
		if (changed != 1) throw conflict("Coupon usage limit has been reached");
		return coupon;
	}

	@Transactional
	public void expireStaleOrders() {
		// Workers claim a bounded batch; a concurrent callback owns the same order lock.
		List<Long> ids=jdbc.queryForList("""
			SELECT id FROM orders WHERE status='PENDING' AND expires_at IS NOT NULL
			AND expires_at<=? ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
			""",Long.class,LocalDateTime.now(clock));
		for(long id:ids){
			jdbc.update("UPDATE orders SET status='EXPIRED' WHERE id=? AND status='PENDING'",id);
			jdbc.update("UPDATE payments SET status='CANCELLED' WHERE order_id=? AND status='PENDING'",id);
			releaseReservedCoupon(id);
		}
	}

	private void releaseReservedCoupon(long orderId) {
		List<Redemption> redemptions = jdbc.query("""
				SELECT id, coupon_id FROM coupon_redemptions
				WHERE order_id = ? AND status = 'RESERVED' FOR UPDATE
				""", (rs, row) -> new Redemption(rs.getLong("id"), rs.getLong("coupon_id")), orderId);
		for (Redemption redemption : redemptions) releaseRedemption(redemption);
	}

	private void releaseRedemption(Redemption redemption) {
		int updated = jdbc.update("UPDATE coupon_redemptions SET status = 'RELEASED' WHERE id = ? AND status = 'RESERVED'", redemption.id);
		if (updated == 1) {
			jdbc.update("UPDATE coupons SET used_count = GREATEST(used_count - 1, 0) WHERE id = ?", redemption.couponId);
		}
	}

	private long activeSeats(long offeringId) {
		Long count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id
				LEFT JOIN enrollments e ON e.order_item_id = oi.id
				WHERE oi.offering_id = ? AND (
				 (o.status = 'PENDING' AND o.expires_at > CURRENT_TIMESTAMP(6))
				 OR (o.status = 'PAID' AND (e.id IS NULL OR e.status <> 'CANCELLED')))
				""", Long.class, offeringId);
		return count == null ? 0 : count;
	}

	private OrderResponse orderResponse(long orderId) {
		List<OrderResponse> rows = jdbc.query("""
				SELECT o.id, o.order_number, o.status, o.subtotal, o.discount_amount, o.total_amount,
				       o.currency, c.code AS coupon_code, o.created_at, o.expires_at, o.paid_at
			FROM orders o LEFT JOIN coupons c ON c.id = o.coupon_id WHERE o.id = ?
			""", (rs, row) -> new OrderResponse(rs.getLong("id"), rs.getString("order_number"), rs.getString("status"),
				rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("total_amount"),
				rs.getString("currency"), rs.getString("coupon_code"), local(rs.getTimestamp("created_at")),
				local(rs.getTimestamp("expires_at")), local(rs.getTimestamp("paid_at")),
				items(orderId)), orderId);
		if (rows.isEmpty()) throw notFound("Order not found");
		return rows.getFirst();
	}

	private List<OrderItemResponse> items(long orderId) {
		return jdbc.query("SELECT id, offering_id, offering_title_snapshot, unit_price FROM order_items WHERE order_id = ? ORDER BY id",
				(rs, row) -> new OrderItemResponse(rs.getLong("id"), rs.getLong("offering_id"),
						rs.getString("offering_title_snapshot"), rs.getBigDecimal("unit_price")), orderId);
	}

	private static BigDecimal discount(CouponData coupon, BigDecimal subtotal) {
		BigDecimal amount = "FIXED".equals(coupon.type) ? coupon.value
				: subtotal.multiply(coupon.value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
		if (coupon.maxDiscount != null) amount = amount.min(coupon.maxDiscount);
		return amount.min(subtotal).max(ZERO).setScale(2, RoundingMode.HALF_UP);
	}
	private static void validateIdempotencyKey(String key) {
		if (key == null || key.length() < 8 || key.length() > 100 || !key.matches("[A-Za-z0-9._:-]+"))
			throw badRequest("A valid Idempotency-Key header is required");
	}
	private static String fingerprint(List<Long> ids, String coupon) {
		try {
			String content = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "|" + Objects.toString(coupon, "");
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
	private static LocalDateTime local(java.sql.Timestamp timestamp) { return timestamp == null ? null : timestamp.toLocalDateTime(); }
	private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
	private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
	private static ResponseStatusException badRequest(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

	private record OfferingData(Long id, String code, String cohortName, LocalDate startDate, LocalDate endDate,
			LocalDateTime registrationClosesAt, Short capacity, BigDecimal price, String status, String courseTitle,
			String courseStatus) {}
	private record CouponData(Long id, String code, String type, BigDecimal value, BigDecimal maxDiscount,
			Long maxUses, Long usedCount, Long maxUsesPerUser, LocalDateTime expiresAt, boolean active) {}
	private record Redemption(Long id, Long couponId) {}
	private record OrderOwner(Long userId, String status) {}
	private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
}
