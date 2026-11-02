package com.edushop.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "course_offerings")
public class CourseOffering {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "course_id", nullable = false) private Long courseId;
	@Column(name = "instructor_id", nullable = false) private Long instructorId;
	@Column(name = "offering_code", nullable = false, length = 60) private String offeringCode;
	@Column(name = "cohort_name", length = 120) private String cohortName;
	@Column(name = "start_date", nullable = false) private LocalDate startDate;
	@Column(name = "end_date", nullable = false) private LocalDate endDate;
	@Column(name = "registration_closes_at") private LocalDateTime registrationClosesAt;
	@Column(name = "schedule_summary", nullable = false, length = 255) private String scheduleSummary;
	@Column(nullable = false) private Short capacity;
	@Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
	@Column(nullable = false, length = 3, insertable = false, updatable = false) private String currency = "VND";
	@Enumerated(EnumType.STRING) @Column(nullable = false) private OfferingStatus status;
	@OneToMany(mappedBy = "offering", fetch = FetchType.LAZY)
	@OrderBy("startsAt ASC") private List<ClassSession> sessions = new ArrayList<>();

	protected CourseOffering() {}
	public CourseOffering(Long courseId, Long instructorId, String offeringCode, String cohortName,
			LocalDate startDate, LocalDate endDate, LocalDateTime registrationClosesAt,
			String scheduleSummary, Short capacity, BigDecimal price) {
		this.courseId = Objects.requireNonNull(courseId);
		this.instructorId = Objects.requireNonNull(instructorId);
		this.offeringCode = Objects.requireNonNull(offeringCode);
		this.cohortName = cohortName;
		this.startDate = Objects.requireNonNull(startDate);
		this.endDate = Objects.requireNonNull(endDate);
		this.registrationClosesAt = registrationClosesAt;
		this.scheduleSummary = Objects.requireNonNull(scheduleSummary);
		this.capacity = Objects.requireNonNull(capacity);
		this.price = Objects.requireNonNull(price);
		this.status = OfferingStatus.DRAFT;
	}
	public Long getId() { return id; }
	public Long getCourseId() { return courseId; }
	public Long getInstructorId() { return instructorId; }
	public String getOfferingCode() { return offeringCode; }
	public String getCohortName() { return cohortName; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public LocalDateTime getRegistrationClosesAt() { return registrationClosesAt; }
	public String getScheduleSummary() { return scheduleSummary; }
	public Short getCapacity() { return capacity; }
	public BigDecimal getPrice() { return price; }
	public String getCurrency() { return currency; }
	public OfferingStatus getStatus() { return status; }
	public List<ClassSession> getSessions() { return sessions; }
	public void update(String cohortName, LocalDate startDate, LocalDate endDate,
			LocalDateTime registrationClosesAt, String scheduleSummary, Short capacity, BigDecimal price) {
		this.cohortName = cohortName;
		this.startDate = Objects.requireNonNull(startDate);
		this.endDate = Objects.requireNonNull(endDate);
		this.registrationClosesAt = registrationClosesAt;
		this.scheduleSummary = Objects.requireNonNull(scheduleSummary);
		this.capacity = Objects.requireNonNull(capacity);
		this.price = Objects.requireNonNull(price);
	}
	public void open() { this.status = OfferingStatus.OPEN; }
	public void close() { this.status = OfferingStatus.CLOSED; }
	public void cancel() { this.status = OfferingStatus.CANCELLED; }
	public void complete() { this.status = OfferingStatus.COMPLETED; }
	public void restore() { this.status = OfferingStatus.DRAFT; }
}
