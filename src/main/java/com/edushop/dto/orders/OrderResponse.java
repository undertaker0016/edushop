package com.edushop.dto.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, String orderNumber, String status, BigDecimal subtotal,
		BigDecimal discountAmount, BigDecimal totalAmount, String currency, String couponCode,
		LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime paidAt,
		List<OrderItemResponse> items) {}
