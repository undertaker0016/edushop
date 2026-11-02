package com.edushop.controller;

import com.edushop.dto.payments.PaymentResponse;
import com.edushop.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("!test & !prod")
@ConditionalOnProperty(name = "payment.mode", havingValue = "ZALOPAY_SANDBOX")
public class ZaloPayController {
  private final ZaloPayGateway gateway;
  private final PaymentService payments;
  private final ConcurrentHashMap<Long, Long> lastQuery = new ConcurrentHashMap<>();

  public ZaloPayController(ZaloPayGateway gateway, PaymentService payments) {
    this.gateway = gateway;
    this.payments = payments;
  }

  @PostMapping("/api/v1/payments/webhooks/zalopay")
  public Map<String, Object> callback(@RequestBody JsonNode body) {
    try {
      JsonNode data = gateway.verifiedCallback(body);
      String ref = data.path("app_trans_id").asText();
      payments.applyProviderReference(
          gateway.providerName(),
          ref,
          ref,
          data.path("zp_trans_id").asText(),
          new BigDecimal(data.path("amount").asText()),
          true);
      return Map.of("return_code", 1, "return_message", "success");
    } catch (ResponseStatusException ex) {
      return Map.of("return_code", 2, "return_message", "invalid");
    } catch (RuntimeException ex) {
      return Map.of("return_code", 0, "return_message", "retry");
    }
  }

  @PostMapping("/api/v1/payments/{id}/verify")
  public PaymentResponse verify(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    long user = Long.parseLong(jwt.getSubject());
    var p = payments.payment(id);
    long order = ((Number) p.get("order_id")).longValue();
    payments.listForOrder(
        user, false, order); // Ownership is checked before making any provider call.
    check(id, p);
    return payments.listForOrder(user, false, order).stream()
        .filter(row -> row.id() == id)
        .findFirst()
        .orElseThrow();
  }

  @PostMapping("/api/v1/admin/payments/{id}/reconcile")
  public Map<String, Object> reconcile(@PathVariable long id) {
    check(id, payments.payment(id));
    return Map.of("return_code", "SUCCEEDED".equals(payments.payment(id).get("status")) ? 1 : 3);
  }

  private void check(long id, Map<String, Object> p) {
    if (!gateway.providerName().equals(p.get("provider")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Giao dịch không thuộc ZaloPay sandbox.");
    long now = System.currentTimeMillis();
    if (lastQuery.size() >= 10000)
      lastQuery.entrySet().removeIf(entry -> now - entry.getValue() >= 15000);
    if (lastQuery.size() >= 10000 && !lastQuery.containsKey(id))
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng kiểm tra lại sau.");
    AtomicBoolean claimed = new AtomicBoolean();
    lastQuery.compute(
        id,
        (key, last) -> {
          if (last == null || now - last >= 15000) {
            claimed.set(true);
            return now;
          }
          return last;
        });
    if (!claimed.get()) return;
    String ref = (String) p.get("provider_order_ref");
    var result = gateway.query(ref);
    if (result.path("return_code").asInt() == 1) {
      if (!result.path("amount").isIntegralNumber()
          || !result.path("zp_trans_id").isIntegralNumber()
          || result.path("zp_trans_id").asLong() <= 0)
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "ZaloPay trả về dữ liệu giao dịch không hợp lệ.");
      payments.applyProviderReference(
          gateway.providerName(),
          ref,
          ref,
          result.path("zp_trans_id").asText(),
          new BigDecimal(result.path("amount").asText()),
          true);
    }
  }
}
