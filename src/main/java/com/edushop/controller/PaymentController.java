package com.edushop.controller;

import com.edushop.dto.payments.PaymentResponse;
import com.edushop.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
	private final PaymentService service;
	public PaymentController(PaymentService service) { this.service = service; }

	@PostMapping("/orders/{orderId}/initiate")
	@Operation(summary = "Start payment for a pending nonzero order; unavailable until a real provider is configured")
	public PaymentResponse initiate(@AuthenticationPrincipal Jwt jwt,
			@PathVariable long orderId,
			@Parameter(description = "Unique retry key for this payment attempt", required = true)
			@RequestHeader("Idempotency-Key") String key) {
		return service.initiate(userId(jwt), false, orderId, key);
	}

	@GetMapping("/orders/{orderId}")
	@Operation(summary = "List payment attempts for an order owned by the authenticated user")
	public List<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt,
			@PathVariable long orderId) {
		return service.listForOrder(userId(jwt), false, orderId);
	}

	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
