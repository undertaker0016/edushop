package com.edushop.dto.auth;

import jakarta.validation.constraints.*;

public record ProfileUpdateRequest(
    @NotBlank @Size(max = 100) String fullName,
    @Size(max = 20) String phone,
    @Size(max = 500) String avatarUrl) {
  public ProfileUpdateRequest(String fullName, String phone) {
    this(fullName, phone, null);
  }
}
