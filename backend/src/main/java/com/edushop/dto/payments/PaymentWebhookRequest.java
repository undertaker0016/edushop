package com.edushop.dto.payments;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentWebhookRequest(
		@NotBlank String providerTransactionId,
		@NotNull Long orderId,
		@NotNull @DecimalMin("0.00") BigDecimal amount,
		@NotBlank String currency,
		@NotNull Boolean succeeded) {
}
