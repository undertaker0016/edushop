package com.edushop.dto.offering;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OfferingWriteRequest(
		@NotNull Long courseId,
		Long instructorId,
		@NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9_-]+") String offeringCode,
		@Size(max = 120) String cohortName,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		LocalDateTime registrationClosesAt,
		@NotBlank @Size(max = 255) String scheduleSummary,
		@NotNull @Min(1) @Max(32767) Short capacity,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price) {}
