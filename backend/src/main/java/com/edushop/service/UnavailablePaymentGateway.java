package com.edushop.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Component
@Profile("!test")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="payment.mode",havingValue="UNAVAILABLE",matchIfMissing=true)
public class UnavailablePaymentGateway implements PaymentGateway {
	@Override
	public GatewaySession initiate(String orderNumber, BigDecimal amount, String currency, String key) {
		throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"No payment provider is configured; the order remains pending until its reservation expires");
	}

	@Override
	public String providerName() {
		return "UNCONFIGURED";
	}
}
