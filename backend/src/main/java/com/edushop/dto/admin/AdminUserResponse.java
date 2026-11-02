package com.edushop.dto.admin;

import java.util.List;

public record AdminUserResponse(Long id, String fullName, String email, String status, List<String> roles) {}
