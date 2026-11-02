package com.edushop.dto.instructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstructorProfileRequest(
    @NotBlank @Size(max = 100) String fullName,
    @Size(max = 10000) String bio,
    @Size(max = 500) String avatarUrl,
    @Email @Size(max = 255) String contactEmail,
    @Size(max = 20) String phone,
    @Size(max = 40) String zalo,
    @Size(max = 500) String facebookUrl) {}
