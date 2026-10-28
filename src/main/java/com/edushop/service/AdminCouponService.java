package com.edushop.service;

import com.edushop.dto.admin.CouponCreateRequest;
import com.edushop.dto.admin.CouponResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class AdminCouponService {
	private final JdbcTemplate jdbc;

	public AdminCouponService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public CouponResponse create(CouponCreateRequest request) {
		if ("PERCENT".equals(request.discountType()) && request.discountValue().compareTo(new BigDecimal("100")) > 0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage discount cannot exceed 100");
		String code = request.code().trim().toUpperCase(Locale.ROOT);
		try {
			jdbc.update("""
					INSERT INTO coupons (code, discount_type, discount_value, max_discount_amount,
					 max_uses, max_uses_per_user, expires_at, is_active)
					VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
					""", code, request.discountType(), request.discountValue(), request.maxDiscountAmount(),
					request.maxUses(), request.maxUsesPerUser() == null ? 1 : request.maxUsesPerUser(),
					request.expiresAt());
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists or violates its limits");
		}
		return find(code);
	}

	@Transactional(readOnly = true)
	public List<CouponResponse> list() {
		return jdbc.query(
				"SELECT code, discount_type, discount_value, max_discount_amount, max_uses, used_count, max_uses_per_user, expires_at, is_active FROM coupons ORDER BY code",
				(rs, row) -> new CouponResponse(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
						rs.getBigDecimal(4),
						nullableLong(rs.getObject(5)), rs.getLong(6), nullableLong(rs.getObject(7)),
						rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime(), rs.getBoolean(9)));
	}

	@Transactional
	public CouponResponse setActive(String code, boolean active) {
		int updated = jdbc.update("UPDATE coupons SET is_active = ? WHERE code = ?", active,
				code.trim().toUpperCase(Locale.ROOT));
		if (updated == 0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found");
		return find(code.trim().toUpperCase(Locale.ROOT));
	}

	@Transactional(readOnly=true)
	public CouponResponse find(String code) {
		if(jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE code=?",Long.class,code)==0)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Coupon not found");
		return jdbc.queryForObject(
				"SELECT code, discount_type, discount_value, max_discount_amount, max_uses, used_count, max_uses_per_user, expires_at, is_active FROM coupons WHERE code = ?",
				(rs, row) -> new CouponResponse(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
						rs.getBigDecimal(4),
						nullableLong(rs.getObject(5)), rs.getLong(6), nullableLong(rs.getObject(7)),
						rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime(), rs.getBoolean(9)),
				code);
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}

	@Transactional
	public CouponResponse update(String code,CouponCreateRequest r) {
		var ids=jdbc.queryForList("SELECT id FROM coupons WHERE code=? FOR UPDATE",Long.class,code);
		if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Coupon not found");
		if(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE coupon_id=?",Long.class,ids.getFirst())>0)
			throw new ResponseStatusException(HttpStatus.CONFLICT,"Used coupon is immutable; deactivate it and create a new code");
		if("PERCENT".equals(r.discountType()) && r.discountValue().compareTo(new BigDecimal("100"))>0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Percentage cannot exceed 100");
		jdbc.update("UPDATE coupons SET code=?,discount_type=?,discount_value=?,max_discount_amount=?,max_uses=?,max_uses_per_user=?,expires_at=? WHERE id=?",
			r.code().toUpperCase(Locale.ROOT),r.discountType(),r.discountValue(),r.maxDiscountAmount(),r.maxUses(),r.maxUsesPerUser()==null?1:r.maxUsesPerUser(),r.expiresAt(),ids.getFirst());
		return find(r.code().toUpperCase(Locale.ROOT));
	}
	@Transactional(readOnly=true)
	public java.util.List<java.util.Map<String,Object>> history(String code) {
		find(code);
		return jdbc.queryForList("SELECT cr.id,cr.user_id AS userId,cr.order_id AS orderId,cr.status,cr.created_at AS createdAt FROM coupon_redemptions cr JOIN coupons c ON c.id=cr.coupon_id WHERE c.code=? ORDER BY cr.id DESC LIMIT 100",code);
	}
}
