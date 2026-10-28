package com.edushop.service;

import com.edushop.dto.offering.*;
import com.edushop.entity.*;
import com.edushop.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class OfferingManagementService {
	private final CourseRepository courses;
	private final CourseOfferingRepository offerings;
	private final ClassSessionRepository sessions;
	private final InstructorRepository instructors;
	private final JdbcTemplate jdbc;
	private final java.time.Clock clock;

	public OfferingManagementService(CourseRepository courses, CourseOfferingRepository offerings,
			ClassSessionRepository sessions, InstructorRepository instructors, JdbcTemplate jdbc,
			java.time.Clock clock) {
		this.courses = courses;
		this.offerings = offerings;
		this.sessions = sessions;
		this.instructors = instructors;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public ManagedOfferingResponse create(long userId, boolean admin, OfferingWriteRequest request) {
		var course = courses.findById(request.courseId())
				.orElseThrow(() -> notFound("Course not found"));
		if (!admin && !course.getCreatedByUserId().equals(userId))
			throw notFound("Course not found");
		Instructor instructor = admin
				? instructors.findById(request.instructorId() == null ? -1L : request.instructorId())
						.orElseThrow(() -> notFound("Instructor not found"))
				: instructors.findByUserId(userId).orElseThrow(
						() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor profile is required"));
		validateDates(request.startDate(), request.endDate(), request.registrationClosesAt());
		try {
			CourseOffering offering = new CourseOffering(course.getId(), instructor.getId(), request.offeringCode(),
					request.cohortName(), request.startDate(), request.endDate(), request.registrationClosesAt(),
					request.scheduleSummary(), request.capacity(), request.price());
			return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Offering code already exists");
		}
	}

	@Transactional(readOnly = true)
	public List<ManagedOfferingResponse> list(long userId, boolean admin) {
		List<CourseOffering> visible;
		if (admin)
			visible = offerings.findAll();
		else {
			List<Long> courseIds = courses.findByCreatedByUserIdOrderByUpdatedAtDesc(userId)
					.stream().map(Course::getId).toList();
			visible = courseIds.stream().flatMap(id -> offerings.findByCourseIdOrderByStartDateAsc(id).stream())
					.toList();
		}
		return visible.stream().map(ManagedOfferingResponse::from).toList();
	}

	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public ManagedOfferingResponse update(long userId, boolean admin, long id, OfferingUpdateRequest request) {
		CourseOffering offering = owned(userId, admin, id, true);
		validateDates(request.startDate(), request.endDate(), request.registrationClosesAt());
		if (offering.getStatus()==OfferingStatus.CANCELLED || offering.getStatus()==OfferingStatus.COMPLETED)
			throw conflict("Terminal offering cannot be edited");
		for(var s:sessions.findByOffering_IdOrderByStartsAtAsc(id)){
			if(s.getStartsAt().toLocalDate().isBefore(request.startDate()) || s.getEndsAt().isAfter(request.endDate().plusDays(1).atStartOfDay()))
				throw conflict("Existing sessions would fall outside the offering dates");
		}
		long committed = bookedCount(id);
		if (request.capacity() < committed)
			throw conflict("Capacity cannot be lower than booked or reserved seats");
		if (hasActiveReservations(id) && (!offering.getStartDate().equals(request.startDate())
				|| !offering.getEndDate().equals(request.endDate())
				|| !offering.getScheduleSummary().equals(request.scheduleSummary()))) {
			throw conflict("Schedule cannot be changed after payment has been completed");
		}
		offering.update(request.cohortName(), request.startDate(), request.endDate(),
				request.registrationClosesAt(), request.scheduleSummary(), request.capacity(), request.price());
		return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
	}

	@Transactional
	public ManagedOfferingResponse changeStatus(long userId, boolean admin, long id, OfferingStatus target) {
		CourseOffering offering = owned(userId, admin, id, true);
		switch (target) {
			case OPEN -> {
				if (offering.getStatus() != OfferingStatus.DRAFT)
					throw conflict("Only draft offerings can be opened");
				Course course = courses.findById(offering.getCourseId())
						.orElseThrow(() -> notFound("Course not found"));
				if (course.getStatus() != CourseStatus.PUBLISHED)
					throw conflict("Publish the course before opening an offering");
				if (offering.getStartDate().isBefore(LocalDate.now(clock)))
					throw conflict("Offering start date has passed");
				var teaching=sessions.findByOffering_IdOrderByStartsAtAsc(id).stream().filter(s -> s.getStatus()!=SessionStatus.CANCELLED).toList();
				if(teaching.isEmpty())throw conflict("Add at least one session before opening");
				var deadline=offering.getRegistrationClosesAt()==null?offering.getStartDate().atStartOfDay():offering.getRegistrationClosesAt();
				if(!deadline.isAfter(java.time.LocalDateTime.now(clock)))throw conflict("Registration deadline has passed");
				offering.open();
			}
			case CLOSED -> {
				if (offering.getStatus() != OfferingStatus.OPEN)
					throw conflict("Only open offerings can be closed");
				offering.close();
			}
			case CANCELLED -> {
				if (bookedCount(id) > 0 || hasPaidOrders(id))
					throw conflict("An offering with reservations or paid orders cannot be cancelled");
				if (offering.getStatus() == OfferingStatus.CANCELLED
						|| offering.getStatus() == OfferingStatus.COMPLETED)
					throw conflict("Offering cannot be cancelled from its current state");
				offering.cancel();
			}
			case COMPLETED -> {
				if((offering.getStatus()!=OfferingStatus.OPEN && offering.getStatus()!=OfferingStatus.CLOSED)
						|| !offering.getEndDate().isBefore(LocalDate.now(clock)))
					throw conflict("Only an ended open or closed offering can complete");
				offering.complete();
			}
			default -> throw conflict("This status transition is not allowed");
		}
		return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
	}

	@Transactional
	public ManagedSessionResponse createSession(long userId, boolean admin, long offeringId,
			SessionWriteRequest request) {
		CourseOffering offering = owned(userId, admin, offeringId, true);
		validateSession(request.startsAt(), request.endsAt());
		validateSessionWithinOffering(offering, request.startsAt(), request.endsAt());
		ensureEditable(offering);
		if (sessions.findByOffering_IdAndSessionNumber(offeringId, request.sessionNumber()).isPresent())
			throw conflict("Session number already exists");
		return ManagedSessionResponse.from(sessions.saveAndFlush(new ClassSession(offering,
				request.sessionNumber(), request.title(), request.startsAt(), request.endsAt(),
				request.meetingProvider(),
				request.meetingUrl(), request.meetingPassword(), request.notes())));
	}

	@Transactional(readOnly = true)
	public List<ManagedSessionResponse> listSessions(long userId, boolean admin, long offeringId) {
		owned(userId, admin, offeringId, false);
		return sessions.findByOffering_IdOrderByStartsAtAsc(offeringId).stream().map(ManagedSessionResponse::from)
				.toList();
	}

	@Transactional
	public ManagedSessionResponse updateSession(long userId, boolean admin, long offeringId, short number,
			SessionWriteRequest request) {
		ensureEditable(owned(userId, admin, offeringId, true));
		if (number != request.sessionNumber())
			throw conflict("Session number cannot be changed");
		validateSession(request.startsAt(), request.endsAt());
		ClassSession session = sessions.findByOffering_IdAndSessionNumber(offeringId, number)
				.orElseThrow(() -> notFound("Session not found"));
		CourseOffering offering = offerings.findById(offeringId)
				.orElseThrow(() -> notFound("Offering not found"));
		validateSessionWithinOffering(offering, request.startsAt(), request.endsAt());
		session.update(request.title(), request.startsAt(), request.endsAt(), request.meetingProvider(),
				request.meetingUrl(), request.meetingPassword(), request.notes());
		notifyLearners(offeringId,"SESSION_UPDATED","Lịch hoặc thông tin phòng học đã thay đổi. Vui lòng xem lịch lớp.");
		return ManagedSessionResponse.from(sessions.saveAndFlush(session));
	}

	@Transactional
	public void cancelSession(long userId, boolean admin, long offeringId, short number) {
		ensureEditable(owned(userId, admin, offeringId, true));
		ClassSession session = sessions.findByOffering_IdAndSessionNumber(offeringId, number)
				.orElseThrow(() -> notFound("Session not found"));
		session.cancel();
		notifyLearners(offeringId,"SESSION_CANCELLED","Một buổi học đã được hủy. Vui lòng xem lịch lớp.");
	}

	@Transactional(readOnly=true)
	public java.util.Map<String,Object> detail(long userId,boolean admin,long id){
		var o=owned(userId,admin,id,false);
		long booked=bookedCount(id);
		return java.util.Map.of("offering",ManagedOfferingResponse.from(o),"bookedSeats",booked,"availableSeats",Math.max(0,o.getCapacity()-booked));
	}
	@Transactional(readOnly=true)
	public java.util.List<java.util.Map<String,Object>> learners(long userId,boolean admin,long id){
		owned(userId,admin,id,false);
		return jdbc.queryForList("""
			SELECT e.id,e.user_id AS userId,u.full_name AS fullName,u.email,u.phone,e.status,e.enrolled_at AS enrolledAt
			FROM enrollments e JOIN users u ON u.id=e.user_id WHERE e.offering_id=? AND e.status='ACTIVE' ORDER BY e.id
			""",id);
	}
	private static void ensureEditable(CourseOffering o){
		if(o.getStatus()==OfferingStatus.CANCELLED || o.getStatus()==OfferingStatus.COMPLETED)
			throw conflict("Terminal offering cannot be edited");
	}
	private void notifyLearners(long id,String type,String message){
		jdbc.update("INSERT INTO notifications(user_id,title,body,type) SELECT user_id,'Cập nhật lớp học',?,? FROM enrollments WHERE offering_id=? AND status='ACTIVE'",message,type,id);
	}

	private CourseOffering owned(long userId, boolean admin, long id, boolean lock) {
		CourseOffering offering = (lock ? offerings.findByIdForUpdate(id) : offerings.findById(id))
				.orElseThrow(() -> notFound("Offering not found"));
		if (!admin) {
			Course course = courses.findById(offering.getCourseId()).orElseThrow(() -> notFound("Offering not found"));
			if (!course.getCreatedByUserId().equals(userId))
				throw notFound("Offering not found");
		}
		return offering;
	}

	private long bookedCount(long offeringId) {
		Long count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id
				LEFT JOIN enrollments e ON e.order_item_id = oi.id
				WHERE oi.offering_id = ? AND (
				 (o.status = 'PENDING' AND (o.expires_at IS NULL OR o.expires_at > CURRENT_TIMESTAMP(6)))
				 OR (o.status = 'PAID' AND (e.id IS NULL OR e.status <> 'CANCELLED')))
				""", Long.class, offeringId);
		return count == null ? 0 : count;
	}

	private boolean hasPaidOrders(long offeringId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE oi.offering_id = ? AND o.status = 'PAID'",
				Integer.class, offeringId);
		return count != null && count > 0;
	}

	private boolean hasActiveReservations(long offeringId) {
		return bookedCount(offeringId) > 0;
	}

	private static void validateDates(LocalDate start, LocalDate end, java.time.LocalDateTime closes) {
		if (end.isBefore(start))
			throw badRequest("End date must not be before start date");
		if (closes != null && closes.toLocalDate().isAfter(start))
			throw badRequest("Registration must close on or before start date");
	}

	private static void validateSession(java.time.LocalDateTime start, java.time.LocalDateTime end) {
		if (!end.isAfter(start))
			throw badRequest("Session end must be after session start");
	}

	private static void validateSessionWithinOffering(CourseOffering offering,
			java.time.LocalDateTime start, java.time.LocalDateTime end) {
		java.time.LocalDateTime offeringStart = offering.getStartDate().atStartOfDay();
		java.time.LocalDateTime offeringEndExclusive = offering.getEndDate().plusDays(1).atStartOfDay();
		if (start.isBefore(offeringStart) || end.isAfter(offeringEndExclusive))
			throw badRequest("Session must be within the offering dates");
	}

	private static ResponseStatusException notFound(String m) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
	}

	private static ResponseStatusException conflict(String m) {
		return new ResponseStatusException(HttpStatus.CONFLICT, m);
	}

	private static ResponseStatusException badRequest(String m) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
	}
}
