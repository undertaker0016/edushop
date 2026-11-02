package com.edushop.dto.offering;

import com.edushop.entity.MeetingProvider;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record SessionWriteRequest(
		@NotNull @Min(1) @Max(32767) Short sessionNumber,
		@NotBlank @Size(max = 150) String title,
		@NotNull LocalDateTime startsAt,
		@NotNull LocalDateTime endsAt,
		MeetingProvider meetingProvider,
		@Size(max = 1000) String meetingUrl,
		@Size(max = 255) String meetingPassword,
		@Size(max = 10000) String notes) {}
