package com.edushop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "phone", length = 20)
	private String phone;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			name = "user_roles",
			joinColumns = @JoinColumn(name = "user_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false)
	)
	private Set<Role> roles = new HashSet<>();

	protected User() {
	}

	public User(String fullName, String email, String passwordHash, String phone) {
		this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
		this.email = Objects.requireNonNull(email, "email must not be null");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		this.phone = phone;
		this.status = "ACTIVE";
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getPhone() {
		return phone;
	}

	public String getStatus() {
		return status;
	}

	public void ban() {
		this.status = "BANNED";
	}

	public void updateProfile(String fullName, String phone) {
		this.fullName = Objects.requireNonNull(fullName).trim();
		this.phone = phone;
	}

	public void changePassword(String encoded) { this.passwordHash = Objects.requireNonNull(encoded); }

	public void activate() {
		this.status = "ACTIVE";
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Set<Role> getRoles() {
		return roles;
	}

	public void addRole(Role role) {
		roles.add(Objects.requireNonNull(role, "role must not be null"));
	}

	public void removeRole(Role role) {
		roles.remove(Objects.requireNonNull(role, "role must not be null"));
	}
}
