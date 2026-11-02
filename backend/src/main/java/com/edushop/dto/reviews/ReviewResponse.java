package com.edushop.dto.reviews;

import java.time.LocalDateTime;

public record ReviewResponse(Long id, String reviewerName, int rating, String comment, LocalDateTime createdAt) {}
