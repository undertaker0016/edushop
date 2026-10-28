package com.edushop.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagWriteRequest(@NotBlank @Size(max = 50) String name) {}
