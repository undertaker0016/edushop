package com.edushop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "tags")
public class Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Integer id;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	protected Tag() {
	}

	public Tag(String name) {
		this.name = Objects.requireNonNull(name, "name must not be null");
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
