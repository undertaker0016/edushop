package com.edushop.dto.auth;
import jakarta.validation.constraints.*;
public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {
    @Override public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
