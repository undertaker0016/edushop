package com.edushop.controller;
import com.edushop.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordResetController {
 private final PasswordResetService service;
 public PasswordResetController(PasswordResetService service) { this.service=service; }
 public record Request(@NotBlank @Email @Size(max=255) String email) {}
 public record Reset(@NotBlank @Email @Size(max=255) String email, @NotBlank @Pattern(regexp="[0-9]{6}") String code, @NotBlank @Size(min=12,max=72) String newPassword) {
  @Override public String toString() { return "PasswordReset[REDACTED]"; }
 }
 @PostMapping("/request")
 public Map<String,String> request(@Valid @RequestBody Request request) {
  service.request(request.email());
  return Map.of("message","Nếu email có tài khoản đang hoạt động, mã xác nhận sẽ được gửi. Có thể gửi lại sau 60 giây.");
 }
 @PostMapping("/reset")
 public Map<String,String> reset(@Valid @RequestBody Reset request) {
  // Throw outside the service transaction so failed-attempt counters are committed.
  if(!service.reset(request.email(),request.code(),request.newPassword()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã không hợp lệ, đã hết hạn hoặc đã vượt số lần thử. Hãy yêu cầu mã mới.");
  return Map.of("message","Đã đặt lại mật khẩu. Bạn có thể đăng nhập.");
 }
}
