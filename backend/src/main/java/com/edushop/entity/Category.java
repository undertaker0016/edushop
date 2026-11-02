package com.edushop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Integer id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "slug", nullable = false, length = 100)
	private String slug;

	@Column(name = "icon", length = 30)
	private String icon;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected Category() {
	}

	public Category(String name, String slug, String icon, Integer sortOrder) {
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.slug = Objects.requireNonNull(slug, "slug must not be null");
		this.icon = icon;
		this.sortOrder = Objects.requireNonNull(sortOrder, "sortOrder must not be null");
		this.active = true;
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getIcon() {
		return icon;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return active;
	}

	public void deactivate() {
		this.active = false;
	}
}
