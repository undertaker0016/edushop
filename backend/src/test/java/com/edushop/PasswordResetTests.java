package com.edushop;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.edushop.service.PasswordResetService;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "edushop.mail.from=test@example.test")
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
class PasswordResetTests {
  @Autowired PasswordResetService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  @MockitoBean JavaMailSender sender;
  String email;

  @BeforeEach
  void account() {
    email = "reset-" + UUID.randomUUID() + "@example.test";
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES ('Reset Test',?,?)",
        email,
        encoder.encode("Original-password-2026"));
  }

  String code() {
    service.request(email);
    var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(captured.capture());
    assertThat(captured.getValue().getFrom()).isEqualTo("EduShop <test@example.test>");
    assertThat(captured.getValue().getTo()).containsExactly(email);
    var matcher = Pattern.compile("[0-9]{6}").matcher(captured.getValue().getText());
    assertThat(matcher.find()).isTrue();
    return matcher.group();
  }

  @Test
  void sendsOncePerCooldownAndConsumesCode() {
    String code = code();
    service.request(email);
    verify(sender, times(1)).send(any(SimpleMailMessage.class));
    assertThat(service.reset(email, code, "Replacement-password-2026")).isTrue();
    String hash =
        jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
    assertThat(encoder.matches("Replacement-password-2026", hash)).isTrue();
    assertThat(service.reset(email, code, "Another-password-2026")).isFalse();
  }

  @Test
  void rejectsExpiredAndUnknownCodes() {
    String code = code();
    jdbc.update(
        "UPDATE password_reset_codes SET expires_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY)"
            + " WHERE user_id=(SELECT id FROM users WHERE email=?)",
        email);
    assertThat(service.reset(email, code, "Replacement-password-2026")).isFalse();
    assertThat(service.reset("missing@example.test", code, "Replacement-password-2026")).isFalse();
  }

  @Test
  void locksAfterFiveWrongAttempts() {
    String code = code();
    String wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 5; i++)
      assertThat(service.reset(email, wrong, "Replacement-password-2026")).isFalse();
    assertThat(service.reset(email, code, "Replacement-password-2026")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM password_reset_codes WHERE user_id=(SELECT id FROM users"
                    + " WHERE email=?)",
                Integer.class,
                email))
        .isEqualTo(5);
  }

  @Test
  void unknownEmailDoesNotSendAndPublicEndpointValidatesBody() throws Exception {
    service.request("not-present@example.test");
    verifyNoInteractions(sender);
    mvc.perform(
            post("/api/v1/auth/password/request")
                .contentType("application/json")
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/password/reset")
                .contentType("application/json")
                .content(
                    "{\"email\":\"nobody@example.test\",\"code\":\"123456\",\"newPassword\":\"Strong-password-2026\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void publicResetAllowsNewPasswordAndRejectsOldPassword() throws Exception {
    String code = code();
    mvc.perform(
            post("/api/v1/auth/password/reset")
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"code\":\""
                        + code
                        + "\",\"newPassword\":\"Replacement-password-2026\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    "{\"email\":\"" + email + "\",\"password\":\"Replacement-password-2026\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"Original-password-2026\"}"))
        .andExpect(status().isUnauthorized());
  }
}
