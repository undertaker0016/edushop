package com.edushop.service;

import com.edushop.dto.instructor.InstructorProfileRequest;
import com.edushop.dto.instructor.InstructorProfileResponse;
import com.edushop.entity.Instructor;
import com.edushop.repository.InstructorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InstructorProfileService {
	private final InstructorRepository instructors;

	public InstructorProfileService(InstructorRepository instructors) {
		this.instructors = instructors;
	}

	@Transactional(readOnly = true)
	public InstructorProfileResponse get(long userId) {
		return InstructorProfileResponse.from(profileFor(userId));
	}

	@Transactional
	public InstructorProfileResponse update(long userId, InstructorProfileRequest request) {
		Instructor profile = profileFor(userId);
		profile.updateProfile(request.fullName().trim(), trimToNull(request.bio()), trimToNull(request.avatarUrl()),
				trimToNull(request.contactEmail()), trimToNull(request.phone()), trimToNull(request.zalo()),
				trimToNull(request.facebookUrl()));
		return InstructorProfileResponse.from(instructors.saveAndFlush(profile));
	}

	private Instructor profileFor(long userId) {
		return instructors.findByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Instructor profile is required"));
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
