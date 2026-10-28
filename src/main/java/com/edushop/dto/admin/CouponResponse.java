package com.edushop.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(String code, String discountType, BigDecimal discountValue,
		BigDecimal maxDiscountAmount, Long maxUses, Long usedCount,
		Long maxUsesPerUser, LocalDateTime expiresAt, boolean active) {}
