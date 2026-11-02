package com.edushop.dto.notifications;

import jakarta.validation.constraints.*;

public record CampaignRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String body,
        @NotBlank @Pattern(regexp = "ANNOUNCEMENT|CLASS_UPDATE|SYSTEM") String type,
        @NotBlank @Pattern(regexp = "ALL_ACTIVE|BUYERS|INSTRUCTORS|COURSE_ACTIVE|OFFERING_ACTIVE|COURSE_PAID|OFFERING_PAID") String audience,
        @Positive Long courseId,
        @Positive Long offeringId) {}
