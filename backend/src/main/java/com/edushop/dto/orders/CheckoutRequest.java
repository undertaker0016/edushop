package com.edushop.dto.orders;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckoutRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> offeringIds,
		@Size(max = 50) String couponCode) {}
