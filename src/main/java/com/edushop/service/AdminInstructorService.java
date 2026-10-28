package com.edushop.service;

import com.edushop.dto.admin.CreateInstructorRequest;
import com.edushop.dto.admin.InstructorResponse;
import com.edushop.entity.Instructor;
import com.edushop.repository.InstructorRepository;
import com.edushop.repository.RoleRepository;
import com.edushop.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminInstructorService {
	private final UserRepository users;
	private final RoleRepository roles;
	private final InstructorRepository instructors;
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	public AdminInstructorService(UserRepository users, RoleRepository roles, InstructorRepository instructors,
			org.springframework.jdbc.core.JdbcTemplate jdbc) {
		this.users = users;
		this.roles = roles;
		this.instructors = instructors;
		this.jdbc = jdbc;
	}

	@Transactional
	public InstructorResponse create(CreateInstructorRequest request) {
		var user = users.findByEmail(request.email().trim().toLowerCase())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		if (!"ACTIVE".equals(user.getStatus()))
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"An inactive account cannot be granted instructor access");
		var role = roles.findByName("INSTRUCTOR")
				.orElseThrow(() -> new IllegalStateException("INSTRUCTOR role is not configured"));
		user.addRole(role);
		var profile = instructors.findByUserId(user.getId()).orElseGet(() ->
				instructors.saveAndFlush(new Instructor(user.getId(), request.fullName().trim())));
		return new InstructorResponse(profile.getId(), user.getId(), profile.getFullName(), user.getEmail());
	}

	@Transactional(readOnly=true)
	public com.edushop.dto.instructor.InstructorProfileResponse get(long id) {
		return com.edushop.dto.instructor.InstructorProfileResponse.from(instructors.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Instructor not found")));
	}
	@Transactional
	public com.edushop.dto.instructor.InstructorProfileResponse update(long id,com.edushop.dto.instructor.InstructorProfileRequest r) {
		var i=instructors.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		i.updateProfile(r.fullName().trim(),r.bio(),r.avatarUrl(),r.contactEmail(),r.phone(),r.zalo(),r.facebookUrl());
		return com.edushop.dto.instructor.InstructorProfileResponse.from(instructors.saveAndFlush(i));
	}
	@Transactional
	public void revoke(long id) {
		var i=instructors.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,i.getUserId());
		long ongoing=jdbc.queryForObject("""
			SELECT COUNT(*) FROM course_offerings o JOIN courses c ON c.id=o.course_id
			WHERE (o.instructor_id=? OR c.created_by_user_id=?)
			AND o.status IN ('DRAFT','OPEN','CLOSED')
			""",Long.class,id,i.getUserId());
		if(ongoing>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Complete or cancel assigned classes before revoking instructor access");
		var u=users.findById(i.getUserId()).orElseThrow();
		u.getRoles().removeIf(r -> "INSTRUCTOR".equals(r.getName()));
	}
}
