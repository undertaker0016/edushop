package com.edushop.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final ObjectProvider<JavaMailSender> mail;
  private final String from;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
      JdbcTemplate jdbc,
      PasswordEncoder encoder,
      ObjectProvider<JavaMailSender> mail,
      @Value("${edushop.mail.from:}") String from) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.mail = mail;
    this.from = from;
  }

  private boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0;
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public void request(String email) {
    var sender = mail.getIfAvailable();
    if (sender == null || from.isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Email chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
    var users =
        jdbc.queryForList("SELECT id,status FROM users WHERE email=? FOR UPDATE", normalize(email));
    if (users.isEmpty() || !"ACTIVE".equals(users.getFirst().get("status"))) return;
    long id = ((Number) users.getFirst().get("id")).longValue();
    var previous =
        jdbc.queryForList(
            "SELECT (next_send_at>CURRENT_TIMESTAMP(6)) AS cooling FROM password_reset_codes WHERE"
                + " user_id=?",
            id);
    // Return the same response for unknown accounts and requests during cooldown.
    if (!previous.isEmpty() && flag(previous.getFirst().get("cooling"))) return;
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
    jdbc.update(
        """
INSERT INTO password_reset_codes(user_id,code_hash,expires_at,next_send_at,attempts,consumed)
VALUES (?,?,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 10 MINUTE),DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 60 SECOND),0,FALSE) ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash),expires_at=VALUES(expires_at),next_send_at=VALUES(next_send_at),attempts=0,consumed=FALSE
""",
        id,
        encoder.encode(code));
    var message = new SimpleMailMessage();
    message.setFrom("EduShop <" + from.trim() + ">");
    message.setTo(normalize(email));
    message.setSubject("EduShop - Mã đặt lại mật khẩu");
    message.setText(
        "Mã xác nhận của bạn: "
            + code
            + "\n"
            + "Mã có hiệu lực 10 phút, chỉ dùng một lần. Không chia sẻ mã này. Nếu không yêu cầu,"
            + " hãy bỏ qua email.");
    try {
      sender.send(message);
    } catch (MailException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Chưa gửi được email. Vui lòng thử lại sau.");
    }
  }

  @Transactional
  public boolean reset(String email, String code, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Mật khẩu vượt quá 72 byte; hãy dùng mật khẩu ngắn hơn.");
    var users =
        jdbc.queryForList("SELECT id,status FROM users WHERE email=? FOR UPDATE", normalize(email));
    if (users.isEmpty() || !"ACTIVE".equals(users.getFirst().get("status"))) return false;
    long id = ((Number) users.getFirst().get("id")).longValue();
    var codes =
        jdbc.queryForList(
            "SELECT code_hash,(expires_at<=CURRENT_TIMESTAMP(6)) AS expired,attempts,consumed FROM"
                + " password_reset_codes WHERE user_id=? FOR UPDATE",
            id);
    if (codes.isEmpty()) return false;
    Map<String, Object> row = codes.getFirst();
    if (flag(row.get("consumed"))
        || ((Number) row.get("attempts")).intValue() >= 5
        || flag(row.get("expired"))) return false;
    jdbc.update("UPDATE password_reset_codes SET attempts=attempts+1 WHERE user_id=?", id);
    if (!encoder.matches(code, (String) row.get("code_hash"))) return false;
    jdbc.update("UPDATE users SET password_hash=? WHERE id=?", encoder.encode(password), id);
    jdbc.update("UPDATE password_reset_codes SET consumed=TRUE WHERE user_id=?", id);
    return true;
  }
}
