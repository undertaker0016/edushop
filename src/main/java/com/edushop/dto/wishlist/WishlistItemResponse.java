package com.edushop.dto.wishlist;

import java.time.LocalDateTime;

public record WishlistItemResponse(Long courseId, String slug, String title, String thumbnailUrl, LocalDateTime addedAt) {}
