package com.edushop.dto.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long orderId, String provider, String status,
		BigDecimal amount, String currency, String providerTransactionId,
		String checkoutUrl, LocalDateTime paidAt) {}
