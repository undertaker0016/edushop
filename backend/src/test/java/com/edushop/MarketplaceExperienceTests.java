package com.edushop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.edushop.dto.admin.CouponCreateRequest;
import com.edushop.dto.offering.SessionWriteRequest;
import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.service.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "edushop.media.dir=../.local/test-media")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class MarketplaceExperienceTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired CourseManagementService courseManager;
  @Autowired AdminCouponService coupons;
  @Autowired CouponPreviewService preview;
  @Autowired CheckoutService checkout;
  @Autowired OfferingManagementService offerings;
  long user, course, offering;
  String unique;
  List<Path> files = new ArrayList<>();

  @BeforeEach
  void setup() {
    unique = "ux" + UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES ('UX Test',?,'unused')",
        unique + "@example.test");
    user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, unique + "@example.test");
    jdbc.update("INSERT INTO instructors(user_id,full_name) VALUES (?,'UX Instructor')", user);
    long teacher =
        jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, user);
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,description,status) VALUES (?,?,?,'Test"
            + " course','PUBLISHED')",
        user,
        unique,
        unique);
    course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, unique);
    jdbc.update(
        "INSERT INTO"
            + " course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status,registration_closes_at)"
            + " VALUES (?,?,?,DATE_ADD(CURRENT_DATE,INTERVAL 14 DAY),DATE_ADD(CURRENT_DATE,INTERVAL"
            + " 42 DAY),'T3-T5',20,300000,'OPEN',DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 DAY))",
        course,
        teacher,
        unique);
    offering =
        jdbc.queryForObject(
            "SELECT id FROM course_offerings WHERE offering_code=?", Long.class, unique);
  }

  @AfterEach
  void cleanup() throws Exception {
    for (Path path : files) Files.deleteIfExists(path);
  }

  @Test
  void imageUploadValidatesBytesAndIsReadable() throws Exception {
    mvc.perform(
            multipart("/api/v1/media")
                .file(
                    new MockMultipartFile("file", "evil.svg", "image/svg+xml", "<svg/>".getBytes()))
                .with(jwt().jwt(j -> j.subject("" + user))))
        .andExpect(status().isBadRequest());
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var result =
        mvc.perform(
                multipart("/api/v1/media")
                    .file(
                        new MockMultipartFile(
                            "file", "cover.png", "image/png", bytes.toByteArray()))
                    .with(jwt().jwt(j -> j.subject("" + user))))
            .andExpect(status().isOk())
            .andReturn();
    var body = mapper.readTree(result.getResponse().getContentAsString());
    String id = body.path("id").asText();
    files.add(Path.of("../.local/test-media", id + ".png"));
    courseManager.update(
        user,
        false,
        course,
        new com.edushop.dto.catalog.CourseWriteRequest(
            unique,
            unique,
            "With uploaded photo",
            body.path("url").asText(),
            List.of(),
            List.of()));
    assertThat(
            jdbc.queryForObject(
                "SELECT thumbnail_media_id FROM courses WHERE id=?", String.class, course))
        .isEqualTo(id);
    mvc.perform(get(body.path("url").asText()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
  }

  @Test
  void catalogUsesRealPriceAndVisibleReviews() throws Exception {
    jdbc.update(
        "INSERT INTO reviews(user_id,course_id,rating,comment) VALUES (?,?,5,'Test only')",
        user,
        course);
    mvc.perform(
            get("/api/v1/courses")
                .param("q", unique)
                .param("sort", "rating_desc")
                .param("minRating", "4")
                .param("maxPrice", "300000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].rating").value(5))
        .andExpect(jsonPath("$.items[0].minPrice").value(300000));
    mvc.perform(get("/api/v1/courses").param("q", unique).param("maxPrice", "0"))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/courses/suggestions").param("q", unique))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].courseId").value(course));
  }

  @Test
  void voucherPreviewAndCheckoutShareMinimumAndCap() {
    String code = unique.substring(0, 20).toUpperCase();
    coupons.create(
        new CouponCreateRequest(
            code,
            "PERCENT",
            new BigDecimal("10"),
            new BigDecimal("20000"),
            100L,
            1L,
            LocalDateTime.now().plusDays(5),
            "Test voucher",
            "Test only",
            new BigDecimal("200000"),
            LocalDateTime.now().minusDays(1)));
    var quote = preview.preview(user, List.of(offering), code);
    assertThat((BigDecimal) quote.get("discountAmount")).isEqualByComparingTo("20000");
    assertThat(jdbc.queryForObject("SELECT used_count FROM coupons WHERE code=?", Long.class, code))
        .isZero();
    var order =
        checkout.checkout(user, "ux-key-" + unique, new CheckoutRequest(List.of(offering), code));
    assertThat(order.totalAmount()).isEqualByComparingTo("280000");
    assertThat(order.discountAmount()).isEqualByComparingTo("20000");
  }

  @Test
  void scheduleChecksOwnershipAndRejectsOverlappingSessions() {
    LocalDateTime start = LocalDate.now().plusDays(14).atTime(19, 0);
    var first =
        new SessionWriteRequest(
            (short) 1, "Buoi 1", start, start.plusHours(1), null, null, null, null);
    var conflict =
        new SessionWriteRequest(
            (short) 2, "Buoi 2", start.plusMinutes(30), start.plusHours(2), null, null, null, null);
    assertThatThrownBy(
            () -> offerings.createSchedule(user, true, offering, List.of(first, conflict)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_sessions WHERE offering_id=?", Long.class, offering))
        .isZero();
  }

  @Test
  void schedulePersistsAllSelectedDates() {
    LocalDateTime start = LocalDate.now().plusDays(14).atTime(19, 0);
    var first =
        new SessionWriteRequest(
            (short) 1, "Buoi 1", start, start.plusHours(1), null, null, null, null);
    var second =
        new SessionWriteRequest(
            (short) 2,
            "Buoi 2",
            start.plusDays(2),
            start.plusDays(2).plusHours(1),
            null,
            null,
            null,
            null);
    assertThat(offerings.createSchedule(user, true, offering, List.of(first, second))).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_sessions WHERE offering_id=?", Long.class, offering))
        .isEqualTo(2);
  }

  @Test
  void contactStoresOnePendingApplication() throws Exception {
    String body =
        "{\"expertise\":\"Java\",\"message\":\"Experience for test\",\"phone\":\"0900000000\"}";
    mvc.perform(
            post("/api/v1/instructor-applications")
                .with(jwt().jwt(j -> j.subject("" + user)))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/instructor-applications")
                .with(jwt().jwt(j -> j.subject("" + user)))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM instructor_applications WHERE user_id=?", Long.class, user))
        .isEqualTo(1);
  }

  @Test
  void futureVoucherCannotBeApplied() {
    String code = unique.substring(0, 20).toUpperCase();
    coupons.create(
        new CouponCreateRequest(
            code,
            "FIXED",
            new BigDecimal("50000"),
            null,
            100L,
            1L,
            LocalDateTime.now().plusDays(5),
            "Future",
            "Test",
            new BigDecimal("500000"),
            LocalDateTime.now().plusDays(1)));
    var available = preview.available(user, List.of(offering));
    assertThat(
            available.stream()
                .filter(
                    item ->
                        ((com.edushop.dto.admin.CouponResponse) item.get("coupon"))
                            .code()
                            .equals(code))
                .findFirst()
                .orElseThrow()
                .get("eligible"))
        .isEqualTo(false);
    assertThatThrownBy(() -> preview.preview(user, List.of(offering), code))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void publishedCourseCanUpdatePresentationWithoutChangingIdentity() {
    var request =
        new com.edushop.dto.catalog.CourseWriteRequest(
            unique + " updated",
            unique,
            "Updated description",
            "/images/courses/design.jpg",
            List.of(),
            List.of());
    var updated = courseManager.update(user, false, course, request);
    assertThat(updated.thumbnailUrl()).isEqualTo("/images/courses/design.jpg");
    assertThat(updated.slug()).isEqualTo(unique);
    assertThatThrownBy(
            () ->
                courseManager.update(
                    user,
                    false,
                    course,
                    new com.edushop.dto.catalog.CourseWriteRequest(
                        "Changed", unique + "-new", "Description", null, List.of(), List.of())))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void rejectsNonFiniteFiltersAndNullSessions() throws Exception {
    mvc.perform(get("/api/v1/courses").param("minRating", "NaN"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/instructor/offerings/" + offering + "/schedule")
                .with(
                    jwt()
                        .jwt(j -> j.subject("" + user))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"sessions\":[null]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assignedTeacherCanListClassWithoutBeingCourseCreator() {
    String email = "assigned-" + unique + "@example.test";
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES ('Assigned Teacher',?,'unused')",
        email);
    long assigned = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    jdbc.update("UPDATE instructors SET user_id=? WHERE user_id=?", assigned, user);
    assertThat(offerings.list(assigned, false))
        .extracting(row -> row.id())
        .containsExactly(offering);
    assertThat(offerings.list(assigned + 999999, false)).isEmpty();
  }
}
