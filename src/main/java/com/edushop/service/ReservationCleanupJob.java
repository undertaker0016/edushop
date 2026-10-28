package com.edushop.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps expired order and coupon reservation state current without requiring a user request. */
@Component
public class ReservationCleanupJob {

	private final CheckoutService checkoutService;

	public ReservationCleanupJob(CheckoutService checkoutService) {
		this.checkoutService = checkoutService;
	}

	@Scheduled(fixedDelayString = "${edushop.checkout.cleanup-delay:60000}", initialDelayString = "${edushop.checkout.cleanup-initial-delay:30000}")
	public void expireReservations() {
		checkoutService.expireStaleOrders();
	}
}
