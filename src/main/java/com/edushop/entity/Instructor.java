package com.edushop.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "instructors")
public class Instructor {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@Column(name = "user_id", nullable = false) private Long userId;
	@Column(name = "full_name", nullable = false, length = 150) private String fullName;
	@Column(columnDefinition = "text") private String bio;
	@Column(name = "avatar_url", length = 500) private String avatarUrl;
	@Column(name = "contact_email", length = 255) private String contactEmail;
	@Column(length = 20) private String phone;
	@Column(length = 40) private String zalo;
	@Column(name = "facebook_url", length = 500) private String facebookUrl;
	@Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
	@Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;

	protected Instructor() {}
	public Instructor(Long userId, String fullName) { this.userId = userId; this.fullName = fullName; }
	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public String getFullName() { return fullName; }
	public String getBio() { return bio; }
	public String getAvatarUrl() { return avatarUrl; }
	public String getContactEmail() { return contactEmail; }
	public String getPhone() { return phone; }
	public String getZalo() { return zalo; }
	public String getFacebookUrl() { return facebookUrl; }

	public void updateProfile(String fullName, String bio, String avatarUrl, String contactEmail,
			String phone, String zalo, String facebookUrl) {
		this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
		this.bio = bio;
		this.avatarUrl = avatarUrl;
		this.contactEmail = contactEmail;
		this.phone = phone;
		this.zalo = zalo;
		this.facebookUrl = facebookUrl;
	}
}
