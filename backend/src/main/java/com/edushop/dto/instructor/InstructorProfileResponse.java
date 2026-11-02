package com.edushop.dto.instructor;

import com.edushop.entity.Instructor;

public record InstructorProfileResponse(Long id, String fullName, String bio, String avatarUrl,
		String contactEmail, String phone, String zalo, String facebookUrl) {
	public static InstructorProfileResponse from(Instructor profile) {
		return new InstructorProfileResponse(profile.getId(), profile.getFullName(), profile.getBio(),
				profile.getAvatarUrl(), profile.getContactEmail(), profile.getPhone(), profile.getZalo(),
				profile.getFacebookUrl());
	}
}
