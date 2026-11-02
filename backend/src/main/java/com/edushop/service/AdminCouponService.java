package com.edushop.service;

import com.edushop.dto.admin.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCouponService {
  private final JdbcTemplate jdbc;

  public AdminCouponService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String COLUMNS =
      "code,discount_type,discount_value,max_discount_amount,max_uses,used_count,max_uses_per_user,expires_at,is_active,title,description,minimum_order_amount,starts_at";

  private CouponResponse map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new CouponResponse(
        rs.getString(1),
        rs.getString(2),
        rs.getBigDecimal(3),
        rs.getBigDecimal(4),
        number(rs.getObject(5)),
        rs.getLong(6),
        number(rs.getObject(7)),
        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime(),
        rs.getBoolean(9),
        rs.getString(10),
        rs.getString(11),
        rs.getBigDecimal(12),
        rs.getTimestamp(13) == null ? null : rs.getTimestamp(13).toLocalDateTime());
  }

  private Long number(Object n) {
    return n == null ? null : ((Number) n).longValue();
  }

  private String normalize(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private void validate(CouponCreateRequest r) {
    if ("PERCENT".equals(r.discountType())
        && r.discountValue().compareTo(new BigDecimal("100")) > 0)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Giảm theo phần trăm không vượt quá 100%.");
    if (r.startsAt() != null && r.expiresAt() != null && !r.expiresAt().isAfter(r.startsAt()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Ngày hết hạn phải sau ngày bắt đầu.");
  }

  @Transactional(readOnly = true)
  public List<CouponResponse> list() {
    return jdbc.query("SELECT " + COLUMNS + " FROM coupons ORDER BY code", this::map);
  }

  @Transactional(readOnly = true)
  public CouponResponse find(String code) {
    var rows =
        jdbc.query("SELECT " + COLUMNS + " FROM coupons WHERE code=?", this::map, normalize(code));
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã giảm giá.");
    return rows.getFirst();
  }

  @Transactional
  public CouponResponse create(CouponCreateRequest r) {
    validate(r);
    String code = normalize(r.code());
    try {
      jdbc.update(
          "INSERT INTO"
              + " coupons(code,discount_type,discount_value,max_discount_amount,max_uses,max_uses_per_user,expires_at,is_active,title,description,minimum_order_amount,starts_at)"
              + " VALUES (?,?,?,?,?,?,?,TRUE,?,?,?,?)",
          code,
          r.discountType(),
          r.discountValue(),
          r.maxDiscountAmount(),
          r.maxUses(),
          r.maxUsesPerUser() == null ? 1 : r.maxUsesPerUser(),
          r.expiresAt(),
          r.title(),
          r.description(),
          r.minimumOrderAmount() == null ? BigDecimal.ZERO : r.minimumOrderAmount(),
          r.startsAt());
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Mã đã tồn tại hoặc giá trị giới hạn không hợp lệ.");
    }
    return find(code);
  }

  @Transactional
  public CouponResponse update(String code, CouponCreateRequest r) {
    validate(r);
    var ids =
        jdbc.queryForList(
            "SELECT id FROM coupons WHERE code=? FOR UPDATE", Long.class, normalize(code));
    if (ids.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã.");
    long id = ids.getFirst();
    if (jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE coupon_id=?", Long.class, id) > 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Mã đã được dùng trong đơn hàng. Hãy tắt và tạo mã mới để giữ lịch sử.");
    try {
      jdbc.update(
          "UPDATE coupons SET"
              + " code=?,discount_type=?,discount_value=?,max_discount_amount=?,max_uses=?,max_uses_per_user=?,expires_at=?,title=?,description=?,minimum_order_amount=?,starts_at=?"
              + " WHERE id=?",
          normalize(r.code()),
          r.discountType(),
          r.discountValue(),
          r.maxDiscountAmount(),
          r.maxUses(),
          r.maxUsesPerUser() == null ? 1 : r.maxUsesPerUser(),
          r.expiresAt(),
          r.title(),
          r.description(),
          r.minimumOrderAmount() == null ? BigDecimal.ZERO : r.minimumOrderAmount(),
          r.startsAt(),
          id);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã hoặc điều kiện không hợp lệ.");
    }
    return find(r.code());
  }

  @Transactional
  public CouponResponse setActive(String code, boolean active) {
    if (jdbc.update("UPDATE coupons SET is_active=? WHERE code=?", active, normalize(code)) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return find(code);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(String code) {
    find(code);
    return jdbc.queryForList(
        "SELECT cr.id,cr.user_id AS userId,cr.order_id AS orderId,cr.status,cr.created_at AS"
            + " createdAt FROM coupon_redemptions cr JOIN coupons c ON c.id=cr.coupon_id WHERE"
            + " c.code=? ORDER BY cr.id DESC LIMIT 100",
        normalize(code));
  }
}
