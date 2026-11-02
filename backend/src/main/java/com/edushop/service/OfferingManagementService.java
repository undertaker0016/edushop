package com.edushop.service;

import com.edushop.dto.offering.*;
import com.edushop.entity.*;
import com.edushop.repository.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfferingManagementService {
  private final CourseRepository courses;
  private final CourseOfferingRepository offerings;
  private final ClassSessionRepository sessions;
  private final InstructorRepository instructors;
  private final JdbcTemplate jdbc;
  private final java.time.Clock clock;

  public OfferingManagementService(
      CourseRepository courses,
      CourseOfferingRepository offerings,
      ClassSessionRepository sessions,
      InstructorRepository instructors,
      JdbcTemplate jdbc,
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
    var course = courses.findById(request.courseId()).orElseThrow(() -> notFound("Không tìm thấy khóa học."));
    if (!admin && !course.getCreatedByUserId().equals(userId))
      throw notFound("Không tìm thấy khóa học.");
    Instructor instructor = admin
        ? instructors
            .findById(request.instructorId() == null ? -1L : request.instructorId())
            .orElseThrow(() -> notFound("Không tìm thấy giảng viên."))
        : instructors
            .findByUserId(userId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tài khoản chưa có hồ sơ giảng viên."));
    validateDates(request.startDate(), request.endDate(), request.registrationClosesAt());
    try {
      CourseOffering offering = new CourseOffering(
          course.getId(),
          instructor.getId(),
          request.offeringCode(),
          request.cohortName(),
          request.startDate(),
          request.endDate(),
          request.registrationClosesAt(),
          request.scheduleSummary(),
          request.capacity(),
          request.price());
      return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại. Hãy chọn mã khác.");
    }
  }

  @Transactional(readOnly = true)
  public List<ManagedOfferingResponse> list(long userId, boolean admin) {
    List<CourseOffering> visible;
    if (admin)
      visible = offerings.findAll();
    else {
      List<Long> courseIds = courses.findByCreatedByUserIdOrderByUpdatedAtDesc(userId).stream()
          .map(Course::getId)
          .toList();
      var permitted = new java.util.LinkedHashMap<Long, CourseOffering>();
      for (Long courseId : courseIds) {
        for (var row : offerings.findByCourseIdOrderByStartDateAsc(courseId))
          permitted.put(row.getId(), row);
      }
      instructors
          .findByUserId(userId)
          .ifPresent(
              profile -> {
                for (var row : offerings.findByInstructorIdOrderByStartDateAsc(profile.getId()))
                  permitted.put(row.getId(), row);
              });
      visible = new java.util.ArrayList<>(permitted.values());
    }
    return visible.stream().map(ManagedOfferingResponse::from).toList();
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public ManagedOfferingResponse update(
      long userId, boolean admin, long id, OfferingUpdateRequest request) {
    return update(userId, admin, id, request, true);
  }

  /**
   * Updates the offering and its whole schedule in one transaction, so the class
   * date range and
   * the sessions can change together (e.g. moving or adding sessions after
   * learners enrolled).
   */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public java.util.Map<String, Object> updateWithSchedule(
      long userId,
      boolean admin,
      long id,
      OfferingUpdateRequest request,
      java.util.List<SessionWriteRequest> rows) {
    var offering = update(userId, admin, id, request, false);
    var schedule = replaceSchedule(userId, admin, id, rows);
    return java.util.Map.of("offering", offering, "sessions", schedule);
  }

  private ManagedOfferingResponse update(
      long userId,
      boolean admin,
      long id,
      OfferingUpdateRequest request,
      boolean checkSessionRange) {
    CourseOffering offering = owned(userId, admin, id, true);
    validateDates(request.startDate(), request.endDate(), request.registrationClosesAt());
    if (offering.getStatus() == OfferingStatus.CANCELLED
        || offering.getStatus() == OfferingStatus.COMPLETED)
      throw conflict("Lớp đã hủy hoặc hoàn thành nên không thể chỉnh sửa.");
    if (checkSessionRange) {
      for (var s : sessions.findByOffering_IdOrderByStartsAtAsc(id)) {
        if (s.getStatus() == SessionStatus.CANCELLED)
          continue;
        if (s.getStartsAt().toLocalDate().isBefore(request.startDate())
            || s.getEndsAt().isAfter(request.endDate().plusDays(1).atStartOfDay()))
          throw conflict(
              "Ngày buổi học hiện tại nằm ngoài khoảng ngày lớp mới. Hãy sửa lịch học trước.");
      }
    }
    long committed = bookedCount(id);
    if (request.capacity() < committed)
      throw conflict("Sĩ số không thể thấp hơn số chỗ đã được giữ hoặc thanh toán.");
    offering.update(
        request.cohortName(),
        request.startDate(),
        request.endDate(),
        request.registrationClosesAt(),
        request.scheduleSummary(),
        request.capacity(),
        request.price());
    return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
  }

  @Transactional
  public ManagedOfferingResponse changeStatus(
      long userId, boolean admin, long id, OfferingStatus target) {
    CourseOffering offering = owned(userId, admin, id, true);
    switch (target) {
      case OPEN -> {
        if (offering.getStatus() != OfferingStatus.DRAFT
            && offering.getStatus() != OfferingStatus.CLOSED)
          throw conflict("Chỉ có thể mở bán lớp bản nháp hoặc mở lại lớp đã đóng.");
        Course course = courses
            .findById(offering.getCourseId())
            .orElseThrow(() -> notFound("Không tìm thấy khóa học."));
        if (course.getStatus() != CourseStatus.PUBLISHED)
          throw conflict("Hãy xuất bản khóa học trước khi mở bán lớp.");
        if (offering.getStartDate().isBefore(LocalDate.now(clock)))
          throw conflict("Ngày khai giảng đã qua.");
        var teaching = sessions.findByOffering_IdOrderByStartsAtAsc(id).stream()
            .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
            .toList();
        if (teaching.isEmpty())
          throw conflict("Hãy thêm ít nhất một buổi học trước khi mở bán.");
        var deadline = offering.getRegistrationClosesAt() == null
            ? offering.getStartDate().atStartOfDay()
            : offering.getRegistrationClosesAt();
        if (!deadline.isAfter(java.time.LocalDateTime.now(clock)))
          throw conflict("Hạn đăng ký lớp đã qua. Hãy gia hạn đăng ký trước khi mở bán.");
        offering.open();
      }
      case CLOSED -> {
        if (offering.getStatus() != OfferingStatus.OPEN)
          throw conflict("Chỉ có thể đóng lớp đang mở bán.");
        offering.close();
      }
      case CANCELLED -> {
        if (bookedCount(id) > 0 || hasPaidOrders(id))
          throw conflict("Không thể hủy lớp đã có chỗ được giữ hoặc đơn thanh toán.");
        if (offering.getStatus() == OfferingStatus.CANCELLED
            || offering.getStatus() == OfferingStatus.COMPLETED)
          throw conflict("Không thể hủy lớp ở trạng thái hiện tại.");
        offering.cancel();
      }
      case COMPLETED -> {
        if ((offering.getStatus() != OfferingStatus.OPEN
            && offering.getStatus() != OfferingStatus.CLOSED)
            || !offering.getEndDate().isBefore(LocalDate.now(clock)))
          throw conflict("Chỉ có thể hoàn thành lớp đã kết thúc và đang mở hoặc đã đóng.");
        offering.complete();
      }
      default -> throw conflict("Không thể chuyển lớp sang trạng thái này.");
    }
    return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
  }

  /**
   * Brings a cancelled class back as a draft so the instructor can edit and
   * reopen it.
   */
  @Transactional
  public ManagedOfferingResponse restore(long userId, boolean admin, long id) {
    CourseOffering offering = owned(userId, admin, id, true);
    if (offering.getStatus() != OfferingStatus.CANCELLED)
      throw conflict("Chỉ có thể khôi phục lớp đã hủy.");
    offering.restore();
    return ManagedOfferingResponse.from(offerings.saveAndFlush(offering));
  }

  @Transactional
  public ManagedSessionResponse createSession(
      long userId, boolean admin, long offeringId, SessionWriteRequest request) {
    CourseOffering offering = owned(userId, admin, offeringId, true);
    validateSession(request.startsAt(), request.endsAt());
    validateSessionWithinOffering(offering, request.startsAt(), request.endsAt());
    ensureEditable(offering);
    if (sessions.findByOffering_IdAndSessionNumber(offeringId, request.sessionNumber()).isPresent())
      throw conflict("Số thứ tự buổi học đã tồn tại.");
    return ManagedSessionResponse.from(
        sessions.saveAndFlush(
            new ClassSession(
                offering,
                request.sessionNumber(),
                request.title(),
                request.startsAt(),
                request.endsAt(),
                request.meetingProvider(),
                request.meetingUrl(),
                request.meetingPassword(),
                request.notes())));
  }

  @Transactional(readOnly = true)
  public List<ManagedSessionResponse> listSessions(long userId, boolean admin, long offeringId) {
    owned(userId, admin, offeringId, false);
    return sessions.findByOffering_IdOrderByStartsAtAsc(offeringId).stream()
        .map(ManagedSessionResponse::from)
        .toList();
  }

  @Transactional
  public ManagedSessionResponse updateSession(
      long userId, boolean admin, long offeringId, short number, SessionWriteRequest request) {
    ensureEditable(owned(userId, admin, offeringId, true));
    if (number != request.sessionNumber())
      throw conflict("Không thể đổi số thứ tự của buổi học.");
    validateSession(request.startsAt(), request.endsAt());
    ClassSession session = sessions
        .findByOffering_IdAndSessionNumber(offeringId, number)
        .orElseThrow(() -> notFound("Không tìm thấy buổi học."));
    CourseOffering offering = offerings.findById(offeringId).orElseThrow(() -> notFound("Không tìm thấy lớp học."));
    validateSessionWithinOffering(offering, request.startsAt(), request.endsAt());
    session.update(
        request.title(),
        request.startsAt(),
        request.endsAt(),
        request.meetingProvider(),
        request.meetingUrl(),
        request.meetingPassword(),
        request.notes());
    notifyLearners(
        offeringId,
        "SESSION_UPDATED",
        "Lịch hoặc thông tin phòng học đã thay đổi. Vui lòng xem lịch lớp.");
    return ManagedSessionResponse.from(sessions.saveAndFlush(session));
  }

  @Transactional
  public void cancelSession(long userId, boolean admin, long offeringId, short number) {
    ensureEditable(owned(userId, admin, offeringId, true));
    ClassSession session = sessions
        .findByOffering_IdAndSessionNumber(offeringId, number)
        .orElseThrow(() -> notFound("Không tìm thấy buổi học."));
    session.cancel();
    notifyLearners(
        offeringId, "SESSION_CANCELLED", "Một buổi học đã được hủy. Vui lòng xem lịch lớp.");
  }

  @Transactional(readOnly = true)
  public java.util.Map<String, Object> detail(long userId, boolean admin, long id) {
    var o = owned(userId, admin, id, false);
    long booked = bookedCount(id);
    return java.util.Map.of(
        "offering",
        ManagedOfferingResponse.from(o),
        "bookedSeats",
        booked,
        "availableSeats",
        Math.max(0, o.getCapacity() - booked));
  }

  @Transactional(readOnly = true)
  public java.util.List<java.util.Map<String, Object>> learners(
      long userId, boolean admin, long id) {
    owned(userId, admin, id, false);
    return jdbc.queryForList(
        """
            SELECT e.id,e.user_id AS userId,u.full_name AS fullName,u.email,u.phone,e.status,e.enrolled_at AS enrolledAt
            FROM enrollments e JOIN users u ON u.id=e.user_id WHERE e.offering_id=? AND e.status='ACTIVE' ORDER BY e.id
            """,
        id);
  }

  private static void ensureEditable(CourseOffering o) {
    if (o.getStatus() == OfferingStatus.CANCELLED || o.getStatus() == OfferingStatus.COMPLETED)
      throw conflict("Lớp đã hủy hoặc hoàn thành nên không thể chỉnh sửa.");
  }

  private void notifyLearners(long id, String type, String message) {
    notifyLearners(id, "Cập nhật lớp học", type, message);
  }

  private void notifyLearners(long id, String title, String type, String message) {
    jdbc.update(
        "INSERT INTO notifications(user_id,title,body,type,action_url) SELECT user_id,?,?,?,'/buyer/classroom'"
            + " FROM enrollments WHERE offering_id=? AND status='ACTIVE'",
        title,
        message,
        type,
        id);
  }

  private CourseOffering owned(long userId, boolean admin, long id, boolean lock) {
    CourseOffering offering = (lock ? offerings.findByIdForUpdate(id) : offerings.findById(id))
        .orElseThrow(() -> notFound("Không tìm thấy lớp học."));
    if (!admin) {
      Course course = courses
          .findById(offering.getCourseId())
          .orElseThrow(() -> notFound("Không tìm thấy lớp học."));
      boolean assigned = instructors
          .findById(offering.getInstructorId())
          .map(profile -> profile.getUserId().equals(userId))
          .orElse(false);
      if (!course.getCreatedByUserId().equals(userId) && (lock || !assigned))
        throw notFound("Không tìm thấy lớp học.");
    }
    return offering;
  }

  private long bookedCount(long offeringId) {
    Long count = jdbc.queryForObject(
        """
            SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id
            LEFT JOIN enrollments e ON e.order_item_id = oi.id
            WHERE oi.offering_id = ? AND (
             (o.status = 'PENDING' AND (o.expires_at IS NULL OR o.expires_at > CURRENT_TIMESTAMP(6)))
             OR (o.status = 'PAID' AND (e.id IS NULL OR e.status <> 'CANCELLED')))
            """,
        Long.class,
        offeringId);
    return count == null ? 0 : count;
  }

  private boolean hasPaidOrders(long offeringId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE"
            + " oi.offering_id = ? AND o.status = 'PAID'",
        Integer.class,
        offeringId);
    return count != null && count > 0;
  }

  private boolean hasActiveReservations(long offeringId) {
    return bookedCount(offeringId) > 0;
  }

  private static void validateDates(
      LocalDate start, LocalDate end, java.time.LocalDateTime closes) {
    if (end.isBefore(start))
      throw badRequest("Ngày kết thúc không thể trước ngày bắt đầu.");
    if (closes != null && closes.toLocalDate().isAfter(start))
      throw badRequest("Hạn đăng ký phải trước hoặc cùng ngày khai giảng.");
  }

  private static void validateSession(java.time.LocalDateTime start, java.time.LocalDateTime end) {
    if (!end.isAfter(start))
      throw badRequest("Giờ kết thúc phải sau giờ bắt đầu.");
  }

  private static void validateSessionWithinOffering(
      CourseOffering offering, java.time.LocalDateTime start, java.time.LocalDateTime end) {
    java.time.LocalDateTime offeringStart = offering.getStartDate().atStartOfDay();
    java.time.LocalDateTime offeringEndExclusive = offering.getEndDate().plusDays(1).atStartOfDay();
    if (start.isBefore(offeringStart) || end.isAfter(offeringEndExclusive))
      throw badRequest("Ngày học phải nằm trong khoảng ngày của lớp.");
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

  @Transactional
  public java.util.List<ManagedSessionResponse> createSchedule(
      long userId, boolean admin, long offeringId, java.util.List<SessionWriteRequest> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 100)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lịch học cần từ 1 đến 100 buổi.");
    java.util.List<ManagedSessionResponse> result = new java.util.ArrayList<>();
    var sorted = orderedSchedule(rows);
    for (int n = 1; n < sorted.size(); n++)
      if (sorted.get(n).startsAt().isBefore(sorted.get(n - 1).endsAt()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Các buổi học bị trùng giờ.");
    for (var row : sorted)
      result.add(createSession(userId, admin, offeringId, row));
    return result;
  }

  private java.util.List<SessionWriteRequest> orderedSchedule(java.util.List<SessionWriteRequest> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 100)
      throw badRequest("Lịch học cần từ 1 đến 100 buổi.");
    var chronologicallySorted = rows.stream()
        .sorted(java.util.Comparator.comparing(SessionWriteRequest::startsAt)
            .thenComparing(SessionWriteRequest::endsAt))
        .toList();
    var numbered = new java.util.ArrayList<SessionWriteRequest>();
    for (int i = 0; i < chronologicallySorted.size(); i++) {
      var row = chronologicallySorted.get(i);
      numbered.add(new SessionWriteRequest((short) (i + 1), row.title(), row.startsAt(), row.endsAt(),
          row.meetingProvider(), row.meetingUrl(), row.meetingPassword(), row.notes()));
    }
    return java.util.List.copyOf(numbered);
  }

  @Transactional
  public java.util.List<ManagedSessionResponse> replaceSchedule(long actor, boolean admin, long offeringId,
      java.util.List<SessionWriteRequest> rows) {
    var offering = owned(actor, admin, offeringId, true);
    if (offering.getStatus() == OfferingStatus.CANCELLED || offering.getStatus() == OfferingStatus.COMPLETED)
      throw conflict("Không thể sửa lịch lớp đã hủy hoặc hoàn thành.");
    if (rows == null || rows.isEmpty() || rows.size() > 100)
      throw badRequest("Lịch học cần từ 1 đến 100 buổi.");
    var sorted = orderedSchedule(rows);
    for (int i = 1; i < sorted.size(); i++)
      if (sorted.get(i).startsAt().isBefore(sorted.get(i - 1).endsAt()))
        throw badRequest("Các buổi học bị trùng giờ.");
    if (hasActiveReservations(offeringId) || hasPaidOrders(offeringId))
      return rescheduleWithLearners(offering, sorted);
    jdbc.update("DELETE FROM class_sessions WHERE offering_id=?", offeringId);
    return createSchedule(actor, admin, offeringId, sorted);
  }

  private static final java.time.format.DateTimeFormatter NOTICE_TIME = java.time.format.DateTimeFormatter
      .ofPattern("HH:mm dd/MM/yyyy");

  /**
   * Applies a new schedule to a class that already has learners without losing
   * history: existing
   * sessions are moved in chronological order, extra rows become new sessions and
   * sessions that
   * are no longer needed are cancelled (not deleted). Learners receive one
   * summary notification.
   */
  private java.util.List<ManagedSessionResponse> rescheduleWithLearners(
      CourseOffering offering, java.util.List<SessionWriteRequest> sorted) {
    long offeringId = offering.getId();
    var all = sessions.findByOffering_IdOrderByStartsAtAsc(offeringId);
    var active = all.stream().filter(s -> s.getStatus() != SessionStatus.CANCELLED).toList();
    int nextNumber = all.stream().mapToInt(s -> s.getSessionNumber()).max().orElse(0) + 1;
    var moved = new java.util.ArrayList<String>();
    var added = new java.util.ArrayList<String>();
    var removed = new java.util.ArrayList<String>();
    var result = new java.util.ArrayList<ManagedSessionResponse>();
    for (int i = 0; i < sorted.size(); i++) {
      var row = sorted.get(i);
      validateSessionWithinOffering(offering, row.startsAt(), row.endsAt());
      if (i < active.size()) {
        var session = active.get(i);
        boolean timeChanged = !session.getStartsAt().equals(row.startsAt())
            || !session.getEndsAt().equals(row.endsAt());
        if (timeChanged)
          moved.add(session.getStartsAt().format(NOTICE_TIME) + " sang " + row.startsAt().format(NOTICE_TIME));
        if (timeChanged || !session.getTitle().equals(row.title())) {
          session.update(
              row.title(),
              row.startsAt(),
              row.endsAt(),
              session.getMeetingProvider(),
              session.getMeetingUrl(),
              session.getMeetingPassword(),
              session.getNotes());
          sessions.save(session);
        }
        result.add(ManagedSessionResponse.from(session));
      } else {
        var created = sessions.save(
            new ClassSession(
                offering,
                (short) nextNumber++,
                row.title(),
                row.startsAt(),
                row.endsAt(),
                row.meetingProvider(),
                row.meetingUrl(),
                row.meetingPassword(),
                row.notes()));
        added.add(row.startsAt().format(NOTICE_TIME));
        result.add(ManagedSessionResponse.from(created));
      }
    }
    for (int i = sorted.size(); i < active.size(); i++) {
      var session = active.get(i);
      session.cancel();
      sessions.save(session);
      removed.add(session.getStartsAt().format(NOTICE_TIME));
    }
    sessions.flush();
    if (!moved.isEmpty() || !added.isEmpty() || !removed.isEmpty()) {
      String name = offering.getCohortName() == null || offering.getCohortName().isBlank()
          ? offering.getOfferingCode()
          : offering.getCohortName();
      var parts = new java.util.ArrayList<String>();
      if (!moved.isEmpty())
        parts.add("Đổi buổi học " + summarize(moved));
      if (!added.isEmpty())
        parts.add("Thêm buổi học lúc " + summarize(added));
      if (!removed.isEmpty())
        parts.add("Hủy buổi học lúc " + summarize(removed));
      String title = added.isEmpty() || !moved.isEmpty() || !removed.isEmpty()
          ? "Lớp " + name + " đổi lịch học"
          : "Lớp " + name + " có thêm buổi học";
      notifyLearners(
          offeringId,
          title.length() > 200 ? title.substring(0, 200) : title,
          "SCHEDULE_CHANGED",
          String.join(". ", parts) + ". Bạn mở lớp học để xem lịch mới nhé.");
    }
    return result;
  }

  private static String summarize(java.util.List<String> items) {
    int shown = Math.min(3, items.size());
    String text = String.join("; ", items.subList(0, shown));
    return items.size() > shown ? text + " và " + (items.size() - shown) + " buổi khác" : text;
  }

  @Transactional
  public ManagedSessionResponse updateRoom(
      long actor, boolean admin, long offeringId, short number, SessionRoomRequest r) {
    owned(actor, admin, offeringId, false);
    var offering =
        offerings.findByIdForUpdate(offeringId).orElseThrow(() -> notFound("Không tìm thấy lớp học."));
    if (offering.getStatus() == OfferingStatus.CANCELLED
        || offering.getStatus() == OfferingStatus.COMPLETED)
      throw conflict("Lớp đã hủy hoặc hoàn thành, không cập nhật phòng học.");
    try {
      var uri = java.net.URI.create(r.meetingUrl());
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Dùng đường dẫn phòng học HTTPS hợp lệ.");
    }
    var session =
        sessions
            .findByOffering_IdAndSessionNumber(offeringId, number)
            .orElseThrow(() -> notFound("Không tìm thấy buổi học."));
    if (session.getStatus() == SessionStatus.CANCELLED
        || session.getStatus() == SessionStatus.COMPLETED)
      throw conflict("Buổi học không còn cho phép sửa phòng.");
    boolean changed =
        !java.util.Objects.equals(session.getMeetingUrl(), r.meetingUrl())
            || !java.util.Objects.equals(session.getMeetingPassword(), r.meetingPassword())
            || session.getMeetingProvider() != r.meetingProvider()
            || !java.util.Objects.equals(session.getNotes(), r.notes());
    if (changed) {
      session.updateRoom(r.meetingProvider(), r.meetingUrl(), r.meetingPassword(), r.notes());
      sessions.saveAndFlush(session);
      notifyLearners(
           "CLASS_UPDATE",
     }
    return ManagedSessionResponse.from(session);
  }
}
