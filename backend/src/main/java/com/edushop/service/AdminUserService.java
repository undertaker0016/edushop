package com.edushop.service;

import com.edushop.dto.admin.AdminUserResponse;
import com.edushop.dto.admin.AdminAccountCreateRequest;
import com.edushop.entity.Instructor;
import com.edushop.entity.User;
import com.edushop.repository.InstructorRepository;
import com.edushop.repository.RoleRepository;
import com.edushop.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;

@Service
public class AdminUserService {
	private final UserRepository users;
	private final RoleRepository roles;
	private final InstructorRepository instructors;
	private final PasswordEncoder passwordEncoder;

	public AdminUserService(UserRepository users, RoleRepository roles, InstructorRepository instructors,
			PasswordEncoder passwordEncoder) {
		this.users = users;
		this.roles = roles;
		this.instructors = instructors;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AdminUserResponse create(AdminAccountCreateRequest request) {
		String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (users.findByEmail(email).isPresent())
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
		String roleName = request.role().trim().toUpperCase(java.util.Locale.ROOT);
		var role = roles.findByName(roleName)
				.orElseThrow(() -> new IllegalStateException("Required role is missing from the database"));
		User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()),
				request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
		user.addRole(role);
		User saved = users.saveAndFlush(user);
		if ("INSTRUCTOR".equals(roleName)) instructors.saveAndFlush(new Instructor(saved.getId(), saved.getFullName()));
		return response(saved);
 	}

	@Transactional(readOnly = true)
	public List<AdminUserResponse> list(int page, int size) {
		if (page < 0 || size < 1 || size > 100)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
		return users.findAll(org.springframework.data.domain.PageRequest.of(page, size)).stream()
				.map(this::response).toList();
	}

	@Transactional
	public AdminUserResponse setStatus(long actorId, long targetId, String status) {
		if (actorId == targetId && "BANNED".equals(status))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrators cannot ban their own account");
		User user = users.findById(targetId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		if ("ACTIVE".equals(status))
			user.activate();
		else if ("BANNED".equals(status))
			user.ban();
		else
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or BANNED");
		return response(users.saveAndFlush(user));
	}

    @Transactional
    public AdminUserResponse setRole(long actorId, long targetId, String name, boolean enabled) {
        if (!List.of("ADMIN", "INSTRUCTOR").contains(name))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role");
        if (actorId == targetId && "ADMIN".equals(name) && !enabled)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove your own administrator role");
        var user = users.findById(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var role = roles.findByName(name).orElseThrow();
        if (enabled) {
            user.addRole(role);
            if ("INSTRUCTOR".equals(name) && instructors.findByUserId(targetId).isEmpty())
                instructors.save(new Instructor(targetId, user.getFullName()));
        } else user.removeRole(role);
        return response(users.saveAndFlush(user));
    }

	private AdminUserResponse response(User user) {
		return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getStatus(),
				user.getRoles().stream().map(role -> role.getName()).sorted().toList());
	}

	@Transactional
	public AdminUserResponse update(long id,com.edushop.dto.auth.ProfileUpdateRequest request) {
		var u=users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
		u.updateProfile(request.fullName(),request.phone());return response(u);
	}
}
