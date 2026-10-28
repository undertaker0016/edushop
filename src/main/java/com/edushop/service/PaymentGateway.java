package com.edushop.service;

import java.math.BigDecimal;

public interface PaymentGateway {
	GatewaySession initiate(String orderNumber, BigDecimal amount, String currency, String idempotencyKey);

	String providerName();

	record GatewaySession(String transactionId, String providerOrderRef, String checkoutUrl) {
	}
}
