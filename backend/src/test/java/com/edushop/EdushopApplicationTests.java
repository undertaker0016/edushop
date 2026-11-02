package com.edushop;

import com.edushop.entity.Role;
import com.edushop.entity.Category;
import com.edushop.entity.Course;
import com.edushop.entity.Tag;
import com.edushop.entity.User;
import com.edushop.repository.CategoryRepository;
import com.edushop.repository.CourseRepository;
import com.edushop.repository.RoleRepository;
import com.edushop.repository.TagRepository;
import com.edushop.repository.UserRepository;
import com.edushop.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EdushopApplicationTests {

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PaymentService paymentService;

	@Test
	void contextLoads() {
	}

	@Test
	void readsSeededRoles() {
		assertThat(roleRepository.findAll())
				.extracting(Role::getName)
				.containsExactlyInAnyOrder("ADMIN", "INSTRUCTOR");
	}

	@Test
	@Transactional
	void persistsUserRoleJoinTable() {
		Role role = roleRepository.findByName("INSTRUCTOR").orElseThrow();
		String email = "slice1-" + UUID.randomUUID() + "@example.test";
		String rawPassword = "test-password-for-this-transaction";
		String passwordHash = passwordEncoder.encode(rawPassword);
		User user = new User("Slice One Test", email, passwordHash, null);
		user.addRole(role);

		userRepository.saveAndFlush(user);

		User reloadedUser = userRepository.findByEmail(email).orElseThrow();
		assertThat(reloadedUser.getRoles())
				.extracting(Role::getName)
				.containsExactly(role.getName());

		UserDetails userDetails = userDetailsService.loadUserByUsername(email);
		assertThat(userDetails.getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_INSTRUCTOR");
		assertThat(passwordEncoder.matches(rawPassword, userDetails.getPassword())).isTrue();
		assertThat(passwordEncoder.matches("wrong-password", userDetails.getPassword())).isFalse();

		assertThat(authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)).isAuthenticated())
				.isTrue();
		assertThatThrownBy(() -> authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(email, "wrong-password")))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@Transactional
	void exposesJwtAuthenticationAndPublicReadRules() throws Exception {
		String email = "auth-api-" + UUID.randomUUID() + "@example.test";
		String password = "transaction-test-password-2026";
		String registrationJson = """
				{"fullName":"Auth API Test","email":"%s","password":"%s"}
				""".formatted(email, password);

		MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(registrationJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(900))
				.andReturn();

		Matcher tokenMatcher = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"")
				.matcher(registration.getResponse().getContentAsString());
		assertThat(tokenMatcher.find()).isTrue();
		String token = tokenMatcher.group(1);
		assertThat(userRepository.findByEmail(email).orElseThrow().getRoles()).isEmpty();

		mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Duplicate\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
		mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"fullName\":\"Invalid\",\"email\":\"bad\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"));

		mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/courses/not-yet-implemented"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		mockMvc.perform(get("/api/v1/tags"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		mockMvc.perform(get("/api/v1/courses?page=0&size=20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));

		mockMvc.perform(get("/api/v1/courses?page=-1&size=20"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
		mockMvc.perform(get("/api/v1/orders/not-yet-implemented")
					.header("Authorization", "Bearer not-a-valid-jwt"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/orders/not-yet-implemented")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/admin/users")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
		mockMvc.perform(get("/api/v1/instructor/courses")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());

		Instant now = Instant.now();
		String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder()
						.issuer("edushop-test")
						.subject(userRepository.findByEmail(email).orElseThrow().getId().toString())
						.issuedAt(now.minusSeconds(120))
						.expiresAt(now.minusSeconds(60))
						.build())).getTokenValue();
		mockMvc.perform(get("/api/v1/orders/not-yet-implemented")
					.header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/auth/me")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.roles").isEmpty())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/users")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());

		User buyer = userRepository.findByEmail(email).orElseThrow();
		buyer.addRole(roleRepository.findByName("INSTRUCTOR").orElseThrow());
		userRepository.saveAndFlush(buyer);
		mockMvc.perform(get("/api/v1/instructor/probe")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());

		buyer.ban();
		userRepository.saveAndFlush(buyer);
		mockMvc.perform(get("/api/v1/orders/not-yet-implemented")
					.header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/api/v1/orders/checkout'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/enrollments/{enrollmentId}/sessions'].get").exists());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	@Transactional
	void publicCourseDetailsExposeOnlyPublishedContent() throws Exception {
		String email = "catalog-" + UUID.randomUUID() + "@example.test";
		User creator = userRepository.saveAndFlush(
				new User("Catalog Test Creator", email, "test-password-hash", null));

		Category activeCategory = new Category("Java", "java", "code", 1);
		Category inactiveCategory = new Category("Hidden", "hidden", null, 2);
		inactiveCategory.deactivate();
		activeCategory = categoryRepository.saveAndFlush(activeCategory);
		inactiveCategory = categoryRepository.saveAndFlush(inactiveCategory);
		Tag tag = tagRepository.saveAndFlush(new Tag("Spring"));

		Course publishedCourse = new Course(
				creator.getId(), "Spring Boot", "spring-boot", "Public description", null);
		publishedCourse.addCategory(activeCategory);
		publishedCourse.addCategory(inactiveCategory);
		publishedCourse.addTag(tag);
		publishedCourse.publish();
		courseRepository.saveAndFlush(publishedCourse);

		Course draftCourse = new Course(creator.getId(), "Draft", "draft-course", null, null);
		courseRepository.saveAndFlush(draftCourse);

		mockMvc.perform(get("/api/v1/courses/spring-boot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("spring-boot"))
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.categories.length()").value(1))
				.andExpect(jsonPath("$.categories[0].slug").value("java"))
				.andExpect(jsonPath("$.tags[0]").value("Spring"));

		mockMvc.perform(get("/api/v1/courses/draft-course"))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void instructorsManageOnlyTheirOwnCourses() throws Exception {
		Role instructorRole = roleRepository.findByName("INSTRUCTOR").orElseThrow();
		User instructor = new User("Course Owner", "owner-" + UUID.randomUUID() + "@example.test", "hash", null);
		instructor.addRole(instructorRole);
		instructor = userRepository.saveAndFlush(instructor);
		User anotherInstructor = new User("Other Instructor", "other-" + UUID.randomUUID() + "@example.test", "hash", null);
		anotherInstructor.addRole(instructorRole);
		anotherInstructor = userRepository.saveAndFlush(anotherInstructor);
		String ownerToken = issueTestToken(instructor.getId());
		String otherToken = issueTestToken(anotherInstructor.getId());
		String slug = "java-basics-" + UUID.randomUUID();
		Category category = categoryRepository.saveAndFlush(new Category("Instructor Category", "instructor-category-" + UUID.randomUUID(), null, 1));
		Tag tag = tagRepository.saveAndFlush(new Tag("Instructor-" + UUID.randomUUID()));

		MvcResult created = mockMvc.perform(post("/api/v1/instructor/courses")
					.header("Authorization", "Bearer " + ownerToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"Java Basics\",\"slug\":\"" + slug
						+ "\",\"description\":\"Intro\",\"categorySlugs\":[\"" + category.getSlug()
						+ "\"],\"tags\":[\"" + tag.getName() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn();
		Matcher idMatcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(created.getResponse().getContentAsString());
		assertThat(idMatcher.find()).isTrue();
		String courseId = idMatcher.group(1);

		mockMvc.perform(put("/api/v1/instructor/courses/" + courseId)
					.header("Authorization", "Bearer " + otherToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"Hijack\",\"slug\":\"hijack-" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/instructor/courses/" + courseId + "/publish")
					.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));
		mockMvc.perform(get("/api/v1/courses/" + slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categories[0].slug").value(category.getSlug()))
				.andExpect(jsonPath("$.tags[0]").value(tag.getName()));
	}

	private String issueTestToken(Long userId) {
		Instant now = Instant.now();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder().issuer("edushop-test").subject(userId.toString())
						.issuedAt(now).expiresAt(now.plusSeconds(900)).build())).getTokenValue();
	}

	@Test
	@Transactional
	void publicOfferingScheduleDoesNotExposeMeetingCredentials() throws Exception {
		User creator = userRepository.saveAndFlush(new User(
				"Offering Test", "offering-" + UUID.randomUUID() + "@example.test", "hash", null));
		Course course = new Course(creator.getId(), "Public Class", "public-class-" + UUID.randomUUID(), null, null);
		course.publish();
		course = courseRepository.saveAndFlush(course);
		jdbcTemplate.update("INSERT INTO instructors (user_id, full_name) VALUES (?, ?)", creator.getId(), "Test Instructor");
		Long instructorId = jdbcTemplate.queryForObject(
				"SELECT id FROM instructors WHERE user_id = ?", Long.class, creator.getId());
		String code = "OFFER-" + UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id, instructor_id, offering_code, start_date, end_date, schedule_summary, capacity, price, status)
				VALUES (?, ?, ?, '2027-01-01', '2027-02-01', 'Saturday mornings', 20, 120000.00, 'OPEN')
				""", course.getId(), instructorId, code);
		Long offeringId = jdbcTemplate.queryForObject(
				"SELECT id FROM course_offerings WHERE offering_code = ?", Long.class, code);
		jdbcTemplate.update("""
				INSERT INTO class_sessions
				(offering_id, session_number, title, starts_at, ends_at, meeting_url, meeting_password)
				VALUES (?, 1, 'Session one', '2027-01-02 09:00:00', '2027-01-02 10:00:00', 'https://secret.invalid', 'secret')
				""", offeringId);

		mockMvc.perform(get("/api/v1/courses/" + course.getSlug() + "/offerings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].currency").value("VND"))
				.andExpect(jsonPath("$[0].price").value(120000.00))
				.andExpect(jsonPath("$[0].sessions[0].title").value("Session one"))
				.andExpect(jsonPath("$[0].sessions[0].meetingUrl").doesNotExist())
				.andExpect(jsonPath("$[0].sessions[0].meetingPassword").doesNotExist());
	}

	@Test
	@Transactional
	void instructorOfferingWorkflowEnforcesOwnershipAndSeparatesMeetingSecrets() throws Exception {
		Role instructorRole = roleRepository.findByName("INSTRUCTOR").orElseThrow();
		User owner = new User("Offering Owner", "off-owner-" + UUID.randomUUID() + "@example.test", "hash", null);
		owner.addRole(instructorRole);
		owner = userRepository.saveAndFlush(owner);
		User other = new User("Other Owner", "off-other-" + UUID.randomUUID() + "@example.test", "hash", null);
		other.addRole(instructorRole);
		other = userRepository.saveAndFlush(other);
		jdbcTemplate.update("INSERT INTO instructors (user_id, full_name) VALUES (?, ?)", owner.getId(), "Offering Owner");
		jdbcTemplate.update("INSERT INTO instructors (user_id, full_name) VALUES (?, ?)", other.getId(), "Other Owner");
		String ownerToken = issueTestToken(owner.getId());
		String otherToken = issueTestToken(other.getId());
		mockMvc.perform(get("/api/v1/instructor/profile")
					.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Offering Owner"));
		mockMvc.perform(put("/api/v1/instructor/profile")
					.header("Authorization", "Bearer " + ownerToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"fullName":"Updated Instructor","bio":"Backend trainer",
						"contactEmail":"instructor@example.test","phone":"0900000000"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Updated Instructor"))
				.andExpect(jsonPath("$.contactEmail").value("instructor@example.test"));
		Course course = new Course(owner.getId(), "Offering Course", "offering-course-" + UUID.randomUUID(), null, null);
		course.publish();
		course = courseRepository.saveAndFlush(course);
		String code = "TEST-" + UUID.randomUUID();

		MvcResult created = mockMvc.perform(post("/api/v1/instructor/offerings")
					.header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"courseId":%d,"offeringCode":"%s","startDate":"2027-01-01","endDate":"2027-02-01",
						"scheduleSummary":"Saturday mornings","capacity":20,"price":150000}
						""".formatted(course.getId(), code)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.currency").value("VND"))
				.andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
		Matcher idMatcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(created.getResponse().getContentAsString());
		assertThat(idMatcher.find()).isTrue();
		String offeringId = idMatcher.group(1);

		mockMvc.perform(post("/api/v1/instructor/offerings/" + offeringId + "/open")
					.header("Authorization", "Bearer " + ownerToken))
			.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/instructor/offerings/" + offeringId + "/sessions")
					.header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"sessionNumber":1,"title":"Session one","startsAt":"2027-01-02T09:00:00",
						"endsAt":"2027-01-02T10:00:00","meetingProvider":"ZOOM",
						"meetingUrl":"https://private.invalid/room","meetingPassword":"keep-private"}
						"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/instructor/offerings/" + offeringId + "/open")
				.header("Authorization", "Bearer " + ownerToken))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

		mockMvc.perform(get("/api/v1/courses/" + course.getSlug() + "/offerings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sessions[0].meetingUrl").doesNotExist())
				.andExpect(jsonPath("$[0].sessions[0].meetingPassword").doesNotExist());
		mockMvc.perform(get("/api/v1/instructor/offerings/" + offeringId + "/sessions")
					.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].meetingUrl").value("https://private.invalid/room"));
		mockMvc.perform(get("/api/v1/instructor/offerings/" + offeringId + "/sessions")
					.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void checkoutIsIdempotentSupportsZeroTotalAndPreservesCancelledEnrollmentHistory() throws Exception {
		User buyer = userRepository.saveAndFlush(new User("Buyer", "checkout-" + UUID.randomUUID() + "@example.test", "hash", null));
		User teacher = new User("Teacher", "checkout-teacher-" + UUID.randomUUID() + "@example.test", "hash", null);
		teacher.addRole(roleRepository.findByName("INSTRUCTOR").orElseThrow());
		teacher = userRepository.saveAndFlush(teacher);
		jdbcTemplate.update("INSERT INTO instructors (user_id, full_name) VALUES (?, ?)", teacher.getId(), "Teacher");
		Long instructorId = jdbcTemplate.queryForObject("SELECT id FROM instructors WHERE user_id = ?", Long.class, teacher.getId());
		Course course = new Course(teacher.getId(), "Checkout Course", "checkout-course-" + UUID.randomUUID(), null, null);
		course.publish();
		course = courseRepository.saveAndFlush(course);
		String zeroCode = "FREE-" + UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status)
				VALUES (?, ?, ?, '2027-01-01','2027-02-01','Weekly',10,0,'OPEN')
				""", course.getId(), instructorId, zeroCode);
		Long freeOffering = jdbcTemplate.queryForObject("SELECT id FROM course_offerings WHERE offering_code = ?", Long.class, zeroCode);
		String paidCode = "PAID-" + UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status)
				VALUES (?, ?, ?, '2027-01-01','2027-02-01','Weekly',10,150000,'OPEN')
				""", course.getId(), instructorId, paidCode);
		Long paidOffering = jdbcTemplate.queryForObject("SELECT id FROM course_offerings WHERE offering_code = ?", Long.class, paidCode);
		jdbcTemplate.update("""
				INSERT INTO class_sessions (offering_id,session_number,title,starts_at,ends_at,meeting_provider,meeting_url,meeting_password)
				VALUES (?,1,'Paid session','2027-01-02 09:00:00','2027-01-02 10:00:00','ZOOM','https://paid-class.invalid','private-pass')
				""", paidOffering);
		String expiringCode = "EXP-" + UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status)
				VALUES (?, ?, ?, '2027-01-01','2027-02-01','Weekly',10,250000,'OPEN')
				""", course.getId(), instructorId, expiringCode);
		Long expiringOffering = jdbcTemplate.queryForObject("SELECT id FROM course_offerings WHERE offering_code = ?", Long.class, expiringCode);
		String token = issueTestToken(buyer.getId());
		String key = "checkout-" + UUID.randomUUID();
		String body = "{\"offeringIds\":[" + freeOffering + "]}";
		MvcResult first = mockMvc.perform(post("/api/v1/orders/checkout")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.totalAmount").value(0.0)).andExpect(jsonPath("$.currency").value("VND"))
				.andExpect(jsonPath("$.items.length()").value(1)).andReturn();
		Matcher orderMatcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(first.getResponse().getContentAsString());
		assertThat(orderMatcher.find()).isTrue();
		String orderId = orderMatcher.group(1);
		mockMvc.perform(post("/api/v1/orders/checkout")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(orderId));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ? AND offering_id = ?",
				Integer.class, buyer.getId(), freeOffering)).isEqualTo(1);

		jdbcTemplate.update("UPDATE enrollments SET status = 'CANCELLED' WHERE user_id = ? AND offering_id = ?",
				buyer.getId(), freeOffering);
		mockMvc.perform(post("/api/v1/orders/checkout")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", "rebuy-" + UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ? AND offering_id = ?",
				Integer.class, buyer.getId(), freeOffering)).isEqualTo(2);

		MvcResult pending = mockMvc.perform(post("/api/v1/orders/checkout")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", "paid-" + UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON).content("{\"offeringIds\":[" + paidOffering + "]}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.totalAmount").value(150000.0)).andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andReturn();
		Matcher pendingId = Pattern.compile("\\\"id\\\":(\\d+)").matcher(pending.getResponse().getContentAsString());
		assertThat(pendingId.find()).isTrue();
		long paidOrderId = Long.parseLong(pendingId.group(1));
		MvcResult payment = mockMvc.perform(post("/api/v1/payments/orders/" + paidOrderId + "/initiate")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", "pay-" + UUID.randomUUID()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("TEST_ONLY"))
				.andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.checkoutUrl").isNotEmpty())
				.andReturn();
		Matcher transactionMatcher = Pattern.compile("\\\"providerTransactionId\\\":\\\"([^\\\"]+)\\\"")
				.matcher(payment.getResponse().getContentAsString());
		assertThat(transactionMatcher.find()).isTrue();
		String transactionId = transactionMatcher.group(1);
		paymentService.applyVerifiedResult("TEST_ONLY", transactionId, paidOrderId,
				new java.math.BigDecimal("150000.00"), "VND", true);
		paymentService.applyVerifiedResult("TEST_ONLY", transactionId, paidOrderId,
				new java.math.BigDecimal("150000.00"), "VND", true);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id=? AND offering_id=? AND status='ACTIVE'",
				Integer.class, buyer.getId(), paidOffering)).isEqualTo(1);
		Long paidEnrollment = jdbcTemplate.queryForObject("SELECT id FROM enrollments WHERE user_id=? AND offering_id=? AND status='ACTIVE'",
				Long.class, buyer.getId(), paidOffering);
		mockMvc.perform(get("/api/v1/enrollments/" + paidEnrollment + "/sessions").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].meetingUrl").value("https://paid-class.invalid"));

		MvcResult expiring = mockMvc.perform(post("/api/v1/orders/checkout")
					.header("Authorization", "Bearer " + token).header("Idempotency-Key", "expires-" + UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON).content("{\"offeringIds\":[" + expiringOffering + "]}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
		Matcher expiringId = Pattern.compile("\\\"id\\\":(\\d+)").matcher(expiring.getResponse().getContentAsString());
		assertThat(expiringId.find()).isTrue();
		jdbcTemplate.update("UPDATE orders SET expires_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE id = ?", Long.parseLong(expiringId.group(1)));
		mockMvc.perform(get("/api/v1/orders/" + expiringId.group(1)).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	@Test
	@Transactional
	void wishlistReviewAndNotificationEndpointsEnforceAccountOwnership() throws Exception {
		User buyer = userRepository.saveAndFlush(new User("Feature Buyer", "feature-" + UUID.randomUUID() + "@example.test", "hash", null));
		User other = userRepository.saveAndFlush(new User("Other Buyer", "feature-other-" + UUID.randomUUID() + "@example.test", "hash", null));
		String token = issueTestToken(buyer.getId());
		String otherToken = issueTestToken(other.getId());
		Course course = new Course(buyer.getId(), "Feature Course", "feature-course-" + UUID.randomUUID(), "Description", null);
		course.publish();
		course = courseRepository.saveAndFlush(course);
		Category category = categoryRepository.saveAndFlush(new Category("Feature Category", "feature-category-" + UUID.randomUUID(), null, 1));
		Tag tag = tagRepository.saveAndFlush(new Tag("Feature-" + UUID.randomUUID()));
		Course savedCourse = courseRepository.findById(course.getId()).orElseThrow();
		savedCourse.addCategory(category); savedCourse.addTag(tag); courseRepository.saveAndFlush(savedCourse);

		mockMvc.perform(post("/api/v1/wishlists/courses/" + course.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/wishlists/courses/" + course.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/wishlists").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
		mockMvc.perform(get("/api/v1/wishlists").header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(put("/api/v1/reviews/courses/" + course.getId()).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Great\"}"))
			.andExpect(status().isForbidden());

		User teacher = new User("Feature Teacher", "feature-teacher-" + UUID.randomUUID() + "@example.test", "hash", null);
		teacher.addRole(roleRepository.findByName("INSTRUCTOR").orElseThrow());
		teacher = userRepository.saveAndFlush(teacher);
		jdbcTemplate.update("INSERT INTO instructors (user_id,full_name) VALUES (?,?)", teacher.getId(), "Feature Teacher");
		Long instructorId = jdbcTemplate.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, teacher.getId());
		String code = "NOTIFY-" + UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status)
				VALUES (?, ?, ?, '2027-01-01','2027-02-01','Weekly',5,0,'OPEN')
				""", course.getId(), instructorId, code);
		Long offeringId = jdbcTemplate.queryForObject("SELECT id FROM course_offerings WHERE offering_code=?", Long.class, code);
		mockMvc.perform(post("/api/v1/orders/checkout").header("Authorization", "Bearer " + token)
					.header("Idempotency-Key", "feature-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"offeringIds\":[" + offeringId + "]}"))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("ENROLLMENT_CREATED"));
		Long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 1", Long.class, buyer.getId());
		mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(put("/api/v1/reviews/courses/" + course.getId()).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comment\":\"Good class\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4));
		mockMvc.perform(put("/api/v1/reviews/courses/" + course.getId()).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Updated review\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(5));
		mockMvc.perform(get("/api/v1/reviews/courses/" + course.getSlug()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/v1/courses?q=Feature&category=" + category.getSlug() + "&tag=" + tag.getName()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@Transactional
	void couponUsesServerTotalsReleasesExpiredReservationAndEnforcesCapacity() throws Exception {
		Role instructorRole = roleRepository.findByName("INSTRUCTOR").orElseThrow();
		User buyer1 = userRepository.saveAndFlush(new User("Coupon Buyer One", "coupon1-" + UUID.randomUUID() + "@example.test", "hash", null));
		User buyer2 = userRepository.saveAndFlush(new User("Coupon Buyer Two", "coupon2-" + UUID.randomUUID() + "@example.test", "hash", null));
		User buyer3 = userRepository.saveAndFlush(new User("Coupon Buyer Three", "coupon3-" + UUID.randomUUID() + "@example.test", "hash", null));
		User teacher = new User("Coupon Teacher", "coupon-teacher-" + UUID.randomUUID() + "@example.test", "hash", null);
		teacher.addRole(instructorRole); teacher = userRepository.saveAndFlush(teacher);
		jdbcTemplate.update("INSERT INTO instructors (user_id,full_name) VALUES (?,?)", teacher.getId(), "Coupon Teacher");
		Long instructorId = jdbcTemplate.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, teacher.getId());
		Course course = new Course(teacher.getId(), "Coupon Course", "coupon-course-" + UUID.randomUUID(), null, null);
		course.publish(); course = courseRepository.saveAndFlush(course);
		String code = "LIMIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		jdbcTemplate.update("""
				INSERT INTO course_offerings
				(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status)
				VALUES (?, ?, ?, '2027-01-01','2027-02-01','Weekly',1,100000,'OPEN')
				""", course.getId(), instructorId, "CAP-" + UUID.randomUUID());
		Long offeringId = jdbcTemplate.queryForObject("SELECT id FROM course_offerings WHERE course_id=?", Long.class, course.getId());
		User admin = new User("Coupon Admin", "coupon-admin-" + UUID.randomUUID() + "@example.test", "hash", null);
		admin.addRole(roleRepository.findByName("ADMIN").orElseThrow()); admin = userRepository.saveAndFlush(admin);
		String adminToken = issueTestToken(admin.getId());
		mockMvc.perform(post("/api/v1/admin/coupons").header("Authorization", "Bearer " + adminToken)
					.contentType(MediaType.APPLICATION_JSON).content("""
						{"code":"%s","discountType":"PERCENT","discountValue":50,"maxUses":1,"maxUsesPerUser":1,"expiresAt":"2027-12-31T23:59:00"}
						""".formatted(code)))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.usedCount").value(0));

		String firstKey = "coupon-" + UUID.randomUUID();
		MvcResult order1 = mockMvc.perform(post("/api/v1/orders/checkout").header("Authorization", "Bearer " + issueTestToken(buyer1.getId()))
					.header("Idempotency-Key", firstKey).contentType(MediaType.APPLICATION_JSON)
					.content("{\"offeringIds\":[" + offeringId + "],\"couponCode\":\"" + code + "\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.subtotal").value(100000.0))
				.andExpect(jsonPath("$.discountAmount").value(50000.0)).andExpect(jsonPath("$.totalAmount").value(50000.0))
				.andExpect(jsonPath("$.status").value("PENDING")).andReturn();
		Matcher order1Id = Pattern.compile("\\\"id\\\":(\\d+)").matcher(order1.getResponse().getContentAsString());
		assertThat(order1Id.find()).isTrue();
		jdbcTemplate.update("UPDATE orders SET expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE id=?", Long.parseLong(order1Id.group(1)));
		mockMvc.perform(post("/api/v1/orders/checkout").header("Authorization", "Bearer " + issueTestToken(buyer2.getId()))
					.header("Idempotency-Key", "coupon-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"offeringIds\":[" + offeringId + "],\"couponCode\":\"" + code + "\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.discountAmount").value(50000.0));
		mockMvc.perform(post("/api/v1/orders/checkout").header("Authorization", "Bearer " + issueTestToken(buyer3.getId()))
					.header("Idempotency-Key", "capacity-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"offeringIds\":[" + offeringId + "]}"))
			.andExpect(status().isConflict());
		assertThat(jdbcTemplate.queryForObject("SELECT used_count FROM coupons WHERE code=?", Integer.class, code)).isEqualTo(1);
	}
}
