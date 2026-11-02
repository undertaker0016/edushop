package com.edushop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.edushop.dto.auth.ProfileUpdateRequest;
import com.edushop.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
class ProfileLifecycleTests {
  @Autowired OfferingManagementService sessions;
  @Autowired CheckoutService checkout;
  @Autowired EnrollmentService enrollments;
  @Autowired JdbcTemplate jdbc;
  @Autowired CourseManagementService courses;
  @Autowired AuthService auth;
  @Autowired InstructorProfileService instructorProfiles;
  @Autowired MockMvc mvc;
  long user, course;
  String unique;

  @BeforeEach
  void setup() {
    unique = "life" + UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES ('Lifecycle',?,'unused')",
        unique + "@example.test");
    user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, unique + "@example.test");
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,status) VALUES"
            + " (?,'Lifecycle',?,'DRAFT')",
        user,
        unique);
    course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, unique);
  }

  @Test
  void avatarPersistsAndMeReturnsIt() throws Exception {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " media_assets(id,owner_user_id,original_name,storage_name,content_type,byte_size)"
            + " VALUES (?,?,'test.png',?,'image/png',10)",
        id,
        user,
        id + ".png");
    String url = "/api/v1/media/" + id;
    mvc.perform(
            put("/api/v1/auth/me")
                .with(jwt().jwt(j -> j.subject("" + user)))
                .contentType("application/json")
                .content(
                    "{\"fullName\":\"Updated\",\"phone\":\"0900000000\",\"avatarUrl\":\""
                        + url
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value(url));
    mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject("" + user))))
        .andExpect(jsonPath("$.avatarUrl").value(url));
  }

  @Test
  void instructorSaveAndRemoveAvatarUpdatesAccountIdentity() {
    jdbc.update(
        "INSERT INTO instructors(user_id,full_name,bio) VALUES (?, 'Before', 'Biography')", user);
    var request =
        new com.edushop.dto.instructor.InstructorProfileRequest(
            "Instructor Updated",
            "Biography",
            "https://example.test/avatar.jpg",
            null,
            null,
            null,
            null);
    instructorProfiles.update(user, request);
    assertThat(auth.getCurrentUser(user).fullName()).isEqualTo("Instructor Updated");
    assertThat(auth.getCurrentUser(user).avatarUrl()).isEqualTo("https://example.test/avatar.jpg");
    instructorProfiles.update(
        user,
        new com.edushop.dto.instructor.InstructorProfileRequest(
            "Instructor Updated", "Biography", "", null, null, null, null));
    assertThat(auth.getCurrentUser(user).avatarUrl()).isNull();
    assertThat(instructorProfiles.get(user).avatarUrl()).isNull();
  }

  @Test
  void accountIdentityUpdatesInstructorWithoutLosingBiography() {
    jdbc.update(
        "INSERT INTO instructors(user_id,full_name,bio) VALUES (?, 'Before', 'Keep this bio')",
        user);
    auth.updateProfile(
        user, new ProfileUpdateRequest("New identity", null, "https://example.test/new.jpg"));
    assertThat(instructorProfiles.get(user).fullName()).isEqualTo("New identity");
    assertThat(instructorProfiles.get(user).avatarUrl()).isEqualTo("https://example.test/new.jpg");
    assertThat(instructorProfiles.get(user).bio()).isEqualTo("Keep this bio");
  }

  @Test
  void avatarWithoutUrlSchemeReturnsValidationError() throws Exception {
    mvc.perform(
            put("/api/v1/auth/me")
                .with(jwt().jwt(j -> j.subject("" + user)))
                .contentType("application/json")
                .content("{\"fullName\":\"Updated\",\"avatarUrl\":\"avatar.png\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cannotUseSomeoneElsesUploadedAvatar() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES ('Other',?,'unused')",
        "other" + unique + "@example.test");
    long other =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, "other" + unique + "@example.test");
    jdbc.update(
        "INSERT INTO"
            + " media_assets(id,owner_user_id,original_name,storage_name,content_type,byte_size)"
            + " VALUES (?,?,'other.png',?,'image/png',10)",
        id,
        other,
        id + ".png");
    assertThatThrownBy(
            () ->
                auth.updateProfile(
                    user, new ProfileUpdateRequest("Test", null, "/api/v1/media/" + id)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void archiveTrashAndRestoreAreDistinct() {
    courses.changeStatus(user, false, course, false);
    assertThat(courses.detail(user, false, course).status().name()).isEqualTo("ARCHIVED");
    assertThat(courses.detail(user, false, course).deletedAt()).isNull();
    courses.restore(user, false, course);
    assertThat(courses.detail(user, false, course).status().name()).isEqualTo("DRAFT");
    courses.trash(user, false, course);
    assertThat(courses.list(user, false)).noneMatch(row -> row.id().equals(course));
    assertThat(courses.listWithTrash(user, false))
        .anyMatch(row -> row.id().equals(course) && row.deletedAt() != null);
    courses.restore(user, false, course);
    assertThat(courses.detail(user, false, course).deletedAt()).isNull();
  }

  long offering() {
    jdbc.update("INSERT INTO instructors(user_id,full_name) VALUES (?,'Teacher')", user);
    long teacher =
        jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, user);
    jdbc.update(
        "INSERT INTO"
            + " course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price)"
            + " VALUES (?,?,?,CURRENT_DATE,DATE_ADD(CURRENT_DATE,INTERVAL 10 DAY),'Test',10,0)",
        course,
        teacher,
        unique);
    return jdbc.queryForObject(
        "SELECT id FROM course_offerings WHERE offering_code=?", Long.class, unique);
  }

  @Test
  void unusedCourseCanBePermanentlyRemovedWithItsUnusedClass() {
    offering();
    courses.trash(user, false, course);
    courses.purge(user, false, course);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE id=?", Long.class, course))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_offerings WHERE course_id=?", Long.class, course))
        .isZero();
  }

  @Test
  void cannotPurgeCourseWithOrderHistory() {
    long offering = offering();
    jdbc.update(
        "INSERT INTO orders(order_number,user_id,subtotal,total_amount,status) VALUES"
            + " (?,?,0,0,'CANCELLED')",
        unique,
        user);
    long order =
        jdbc.queryForObject("SELECT id FROM orders WHERE order_number=?", Long.class, unique);
    jdbc.update(
        "INSERT INTO order_items(order_id,offering_id,offering_title_snapshot,unit_price) VALUES"
            + " (?,?,'Test',0)",
        order,
        offering);
    courses.trash(user, false, course);
    assertThatThrownBy(() -> courses.purge(user, false, course))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id=?", Long.class, order))
        .isEqualTo(1);
  }

  @Test
  void otherUserCannotTrashCourse() {
    assertThatThrownBy(() -> courses.trash(user + 999999, false, course))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void courseMustBeInTrashBeforePurge() {
    assertThatThrownBy(() -> courses.purge(user, false, course))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void roomCanBeAddedAfterEnrollmentWithoutChangingSchedule() throws Exception {
    long oid = offering();
    jdbc.update("UPDATE courses SET status='PUBLISHED' WHERE id=?", course);
    jdbc.update(
        "UPDATE course_offerings SET status='OPEN',start_date=DATE_ADD(CURRENT_DATE,INTERVAL 1"
            + " DAY),registration_closes_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY) WHERE id=?",
        oid);
    java.time.LocalDateTime start = java.time.LocalDate.now().plusDays(1).atTime(19, 0);
    jdbc.update(
        "INSERT INTO class_sessions(offering_id,session_number,title,starts_at,ends_at) VALUES"
            + " (?,1,'Test room',?,?)",
        oid,
        start,
        start.plusHours(1));
    checkout.checkout(
        user, "room-" + unique, new com.edushop.dto.orders.CheckoutRequest(List.of(oid), null));
    var request =
        new com.edushop.dto.offering.SessionRoomRequest(
            com.edushop.entity.MeetingProvider.GOOGLE_MEET,
            "https://meet.example.test/room",
            "demo",
            null);
    var result = sessions.updateRoom(user, false, oid, (short) 1, request);
    assertThat(result.startsAt()).isEqualTo(start);
    assertThat(result.meetingUrl()).isEqualTo(request.meetingUrl());
    long enrollment = enrollments.list(user).getFirst().id();
    assertThat(enrollments.sessions(user, enrollment).getFirst().meetingUrl())
        .isEqualTo(request.meetingUrl());
    long messages =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='CLASS_UPDATE'",
            Long.class,
            user);
    sessions.updateRoom(user, false, oid, (short) 1, request);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='CLASS_UPDATE'",
                Long.class,
                user))
        .isEqualTo(messages);
    mvc.perform(get("/api/v1/courses/" + unique + "/offerings"))
        .andExpect(jsonPath("$[0].sessions[0].title").value("Test room"))
        .andExpect(jsonPath("$[0].sessions[0].meetingUrl").doesNotExist());
  }

  @Test
  void quoteAndCheckoutAcceptMultipleCartItems() throws Exception {
    long first = offering();
    long teacher =
        jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, user);
    jdbc.update("UPDATE courses SET status='PUBLISHED' WHERE id=?", course);
    jdbc.update(
        "UPDATE course_offerings SET status='OPEN',start_date=DATE_ADD(CURRENT_DATE,INTERVAL 1"
            + " DAY),registration_closes_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY) WHERE id=?",
        first);
    jdbc.update(
        "INSERT INTO"
            + " course_offerings(course_id,instructor_id,offering_code,start_date,end_date,registration_closes_at,schedule_summary,capacity,price,status)"
            + " VALUES (?,?,?,DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY),DATE_ADD(CURRENT_DATE,INTERVAL"
            + " 10 DAY),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 DAY),'Test',10,100000,'OPEN')",
        course,
        teacher,
        unique + "-two");
    long second =
        jdbc.queryForObject(
            "SELECT id FROM course_offerings WHERE offering_code=?", Long.class, unique + "-two");
    mvc.perform(
            get("/api/v1/coupons/quote")
                .param("offeringIds", first + "," + second)
                .with(jwt().jwt(j -> j.subject("" + user))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(100000));
    var result =
        checkout.checkout(
            user,
            "multi-" + unique,
            new com.edushop.dto.orders.CheckoutRequest(List.of(first, second), null));
    assertThat(result.items()).hasSize(2);
    assertThat(result.totalAmount()).isEqualByComparingTo("100000");
  }
}
