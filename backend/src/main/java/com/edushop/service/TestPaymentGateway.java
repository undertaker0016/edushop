package com.edushop.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("test & !prod")
public class TestPaymentGateway implements PaymentGateway {
	private final ConcurrentMap<String, GatewaySession> sessions = new ConcurrentHashMap<>();

	@Override
	public GatewaySession initiate(String orderNumber, BigDecimal amount, String currency, String key) {
		return sessions.computeIfAbsent(key, ignored -> {
			String ref = "TEST-" + UUID.randomUUID();
			return new GatewaySession(ref, orderNumber, "https://payments.test.invalid/checkout/" + ref);
		});
	}

	@Override
	public String providerName() {
		return "TEST_ONLY";
	}
}
