package com.edushop.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
    String code,
    String discountType,
    BigDecimal discountValue,
    BigDecimal maxDiscountAmount,
    Long maxUses,
    Long usedCount,
    Long maxUsesPerUser,
    LocalDateTime expiresAt,
    boolean active,
    String title,
    String description,
    BigDecimal minimumOrderAmount,
    LocalDateTime startsAt) {
  public CouponResponse(
      String code,
      String type,
      BigDecimal value,
      BigDecimal cap,
      Long uses,
      Long count,
      Long perUser,
      LocalDateTime expiry,
      boolean active) {
    this(
        code,
        type,
        value,
        cap,
        uses,
        count,
        perUser,
        expiry,
        active,
        null,
        null,
        BigDecimal.ZERO,
        null);
  }
}
