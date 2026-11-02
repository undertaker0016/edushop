package com.edushop.controller;

import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.dto.orders.OrderResponse;
import com.edushop.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Checkout/Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
	private final CheckoutService service;
	public OrderController(CheckoutService service) { this.service = service; }

	@PostMapping("/checkout") @ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an idempotent order for open offerings; prices and discount are calculated by the server")
	public OrderResponse checkout(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "Unique key for this checkout attempt", required = true)
			@RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody CheckoutRequest request) {
		return service.checkout(userId(jwt), key, request);
	}

	@GetMapping
	@Operation(summary = "List the authenticated user's orders; administrators can list all orders")
	public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(userId(jwt), false, page, size);
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "Get an order owned by the authenticated user")
	public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long orderId) {
		return service.get(userId(jwt), false, orderId);
	}

	@DeleteMapping("/{orderId}")
	@Operation(summary = "Cancel your pending order and release its reservation")
	public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt,
			@PathVariable long orderId) {
		service.cancel(userId(jwt), false, orderId);
		return ResponseEntity.noContent().build();
	}

	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
