package com.edushop.dto.auth;

import java.util.List;

public record UserProfileResponse(
		Long id,
		String fullName,
		String email,
		List<String> roles, String phone) {
}
