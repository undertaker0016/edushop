package com.edushop.service;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CouponPreviewService {
  private final JdbcTemplate jdbc;
  private final CatalogService catalog;
  private final AdminCouponService coupons;
  private final java.time.Clock clock;

  public CouponPreviewService(
      JdbcTemplate jdbc,
      CatalogService catalog,
      AdminCouponService coupons,
      java.time.Clock clock) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.coupons = coupons;
    this.clock = clock;
  }

  public BigDecimal subtotal(List<Long> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 20
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách lớp không hợp lệ.");
    BigDecimal sum = BigDecimal.ZERO;
    for (long id : ids) {
      var detail = catalog.getCheckoutOffering(id);
      sum = sum.add(((com.edushop.dto.catalog.OfferingResponse) detail.get("offering")).price());
    }
    return sum;
  }

  public Map<String, Object> evaluate(
      long userId, com.edushop.dto.admin.CouponResponse c, BigDecimal subtotal) {
    String reason = "";
    var now = LocalDateTime.now(clock);
    if (!c.active()) reason = "Mã đã tắt";
    else if (c.startsAt() != null && c.startsAt().isAfter(now))
      reason = "Chưa đến thời gian áp dụng";
    else if (c.expiresAt() != null && !c.expiresAt().isAfter(now)) reason = "Mã đã hết hạn";
    else if (c.maxUses() != null && c.usedCount() >= c.maxUses()) reason = "Đã hết lượt sử dụng";
    else if (subtotal.compareTo(c.minimumOrderAmount()) < 0)
      reason = "Chưa đạt giá trị đơn tối thiểu";
    Long used =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM coupon_redemptions cr JOIN coupons c ON c.id=cr.coupon_id JOIN"
                + " orders o ON o.id=cr.order_id WHERE c.code=? AND cr.user_id=? AND"
                + " (cr.status='CONSUMED' OR (cr.status='RESERVED' AND o.status='PENDING' AND"
                + " o.expires_at>CURRENT_TIMESTAMP))",
            Long.class,
            c.code(),
            userId);
    if (reason.isEmpty() && c.maxUsesPerUser() != null && used >= c.maxUsesPerUser())
      reason = "Bạn đã dùng hết lượt của mã này";
    BigDecimal discount =
        "PERCENT".equals(c.discountType())
            ? subtotal
                .multiply(c.discountValue())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
            : c.discountValue();
    if (c.maxDiscountAmount() != null) discount = discount.min(c.maxDiscountAmount());
    discount = discount.min(subtotal).max(BigDecimal.ZERO);
    if (discount.signum() == 0 && reason.isEmpty()) reason = "Mã không áp dụng cho lớp miễn phí";
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("coupon", c);
    result.put("eligible", reason.isEmpty());
    result.put("reason", reason);
    result.put("subtotal", subtotal);
    result.put("discountAmount", reason.isEmpty() ? discount : BigDecimal.ZERO);
    result.put("totalAmount", reason.isEmpty() ? subtotal.subtract(discount) : subtotal);
    return result;
  }

  public List<Map<String, Object>> available(long userId, List<Long> ids) {
    BigDecimal sum = subtotal(ids);
    return coupons.list().stream()
        .filter(c -> c.active())
        .limit(50)
        .map(c -> evaluate(userId, c, sum))
        .toList();
  }

  public Map<String, Object> preview(long userId, List<Long> ids, String code) {
    var result =
        evaluate(userId, coupons.find(code.trim().toUpperCase(Locale.ROOT)), subtotal(ids));
    if (!Boolean.TRUE.equals(result.get("eligible")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, result.get("reason").toString());
    return result;
  }
}
