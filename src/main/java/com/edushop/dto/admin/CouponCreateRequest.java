package com.edushop.dto.admin;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponCreateRequest(
		@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
		@NotBlank @Pattern(regexp = "PERCENT|FIXED") String discountType,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
		@DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal maxDiscountAmount,
		@Positive @Max(4294967295L) Long maxUses,
		@Positive @Max(4294967295L) Long maxUsesPerUser,
		LocalDateTime expiresAt) {
}
