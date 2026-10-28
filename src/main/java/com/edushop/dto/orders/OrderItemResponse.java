package com.edushop.dto.orders;

import java.math.BigDecimal;

public record OrderItemResponse(Long id, Long offeringId, String title, BigDecimal unitPrice) {}
