package com.edushop.controller;

import com.edushop.dto.payments.PaymentWebhookRequest;
import com.edushop.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@RestController
@org.springframework.context.annotation.Profile("test & !prod")
@RequestMapping("/api/v1/payments/webhooks")
@Tag(name = "Payments")
public class PaymentWebhookController {

	private final PaymentService paymentService;
	private final String webhookSecret;

	public PaymentWebhookController(PaymentService paymentService,
			@Value("${payment.webhook.secret:}") String webhookSecret) {
		this.paymentService = paymentService;
		this.webhookSecret = webhookSecret;
	}

	@PostMapping("/{provider}")
	@Operation(summary = "Apply a provider-signed payment result")
	public void webhook(@PathVariable String provider,
			@RequestHeader(value = "X-Payment-Signature", required = false) String signature,
			@Valid @RequestBody PaymentWebhookRequest request) {
		if (webhookSecret.isBlank())
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Payment webhook verification is not configured");
		if (!isValidSignature(provider, request, signature))
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment webhook signature");

		paymentService.applyVerifiedResult(provider.trim().toUpperCase(Locale.ROOT),
				request.providerTransactionId(), request.orderId(), request.amount(),
				request.currency().trim().toUpperCase(Locale.ROOT), request.succeeded());
	}

	private boolean isValidSignature(String provider, PaymentWebhookRequest request, String supplied) {
		if (supplied == null || supplied.isBlank()) return false;
		String payload = provider.trim().toUpperCase(Locale.ROOT) + "|" + request.orderId()
				+ "|" + request.providerTransactionId() + "|"
				+ request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString() + "|"
				+ request.currency().trim().toUpperCase(Locale.ROOT) + "|" + request.succeeded();
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
			return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
					supplied.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
		} catch (Exception exception) {
			throw new IllegalStateException("Cannot verify payment webhook signature", exception);
		}
	}
}
