package com.edushop.dto.offering;

import com.edushop.entity.MeetingProvider;
import jakarta.validation.constraints.*;

public record SessionRoomRequest(
    @NotNull MeetingProvider meetingProvider,
    @NotBlank @Size(max = 1000) String meetingUrl,
    @Size(max = 255) String meetingPassword,
    @Size(max = 10000) String notes) {}
