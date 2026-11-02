package com.edushop.service;

import java.math.BigDecimal;

public interface PaymentGateway {
  GatewaySession initiate(
      String orderNumber, BigDecimal amount, String currency, String idempotencyKey);

  String providerName();

  default boolean publicDemo() {
    return false;
  }

  default String reference(String key, java.time.Instant now) {
    return "PAY-"
        + java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  record GatewaySession(String transactionId, String providerOrderRef, String checkoutUrl) {}
}
