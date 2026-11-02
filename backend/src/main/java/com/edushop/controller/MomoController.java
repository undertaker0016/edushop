package com.edushop.controller;

import com.edushop.service.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("!test")
@ConditionalOnExpression(
    "'${payment.mode:UNAVAILABLE}' == 'MOMO_SANDBOX' or '${payment.mode:UNAVAILABLE}' =="
        + " 'MOMO_PRODUCTION'")
@Tag(name = "MoMo Payments")
public class MomoController {
  private final MomoGateway momo;
  private final PaymentService payments;

  public MomoController(MomoGateway momo, PaymentService payments) {
    this.momo = momo;
    this.payments = payments;
  }

  @PostMapping("/api/v1/payments/webhooks/momo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ipn(@RequestBody JsonNode body) {
    momo.verifyIpn(body);
    int code = body.path("resultCode").asInt();
    // Only a verified success or explicit user cancellation is finalized.
    // Other results remain uncertain until provider query / reconciliation.
    if (code == 0 || code == 1006) {
      payments.applyProviderReference(
          momo.providerName(),
          MomoGateway.required(body, "orderId"),
          MomoGateway.required(body, "requestId"),
          MomoGateway.required(body, "transId"),
          new BigDecimal(MomoGateway.required(body, "amount")),
          code == 0);
    }
  }

  @PostMapping("/api/v1/admin/payments/{id}/reconcile")
  @SecurityRequirement(name = "bearerAuth")
  public JsonNode reconcile(@PathVariable long id) {
    var p = payments.payment(id);
    if (!momo.providerName().equals(p.get("provider")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider mismatch");
    String ref = (String) p.get("provider_order_ref");
    JsonNode r = momo.query(ref);
    int code = r.path("resultCode").asInt(-1);
    if (code == 0 || code == 1006) {
      payments.applyProviderReference(
          momo.providerName(),
          ref,
          ref,
          MomoGateway.required(r, "transId"),
          new BigDecimal(MomoGateway.required(r, "amount")),
          code == 0);
    }
    return r;
  }
}
