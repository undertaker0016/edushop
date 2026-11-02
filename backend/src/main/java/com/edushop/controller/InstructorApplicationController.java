package com.edushop.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InstructorApplicationController {
  private final JdbcTemplate jdbc;
  private final com.edushop.service.AdminInstructorService instructors;

  public InstructorApplicationController(JdbcTemplate jdbc,com.edushop.service.AdminInstructorService instructors) {
    this.jdbc = jdbc;
    this.instructors=instructors;
  }

  public record Application(
      @NotBlank @Size(max = 255) String expertise,
      @NotBlank @Size(max = 10000) String message,
      @Size(max = 20) String phone) {}

  @PostMapping("/api/v1/instructor-applications")
  @Transactional
  public Map<String, String> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Application r) {
    long id = Long.parseLong(jwt.getSubject());
    var user = jdbc.queryForMap("SELECT full_name,email FROM users WHERE id=? FOR UPDATE", id);
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM instructor_applications WHERE user_id=? AND status='PENDING'",
            Long.class,
            id)
        > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã có hồ sơ đang chờ phản hồi.");
    jdbc.update(
        "INSERT INTO instructor_applications(user_id,full_name,email,phone,expertise,message)"
            + " VALUES (?,?,?,?,?,?)",
        id,
        user.get("full_name"),
        user.get("email"),
        r.phone(),
        r.expertise().trim(),
        r.message().trim());
    jdbc.update(
        "INSERT INTO notifications(user_id,title,body,type,action_url) SELECT ur.user_id,'Có hồ sơ đăng ký giảng viên mới',?,'INSTRUCTOR_APPLICATION',? FROM user_roles ur JOIN roles r ON r.id=ur.role_id"
            + " WHERE r.name='ADMIN'",
        user.get("full_name")+" đã gửi hồ sơ. Mở hồ sơ để xem thông tin và cấp quyền.","/admin/instructor-applications?applicationId="+jdbc.queryForObject("SELECT MAX(id) FROM instructor_applications WHERE user_id=?",Long.class,id));
    jdbc.update("INSERT INTO notifications(user_id,title,body,type,action_url) VALUES(?,'Đã nhận hồ sơ giảng viên','Hồ sơ của bạn đang chờ quản trị viên xem xét. Không yêu cầu thanh toán.','INSTRUCTOR_APPLICATION_STATUS','/contact')",id);
    return Map.of("message", "Đã gửi hồ sơ. Trạng thái hiện tại: Chờ duyệt. Không yêu cầu thanh toán.");
  }

  @GetMapping("/api/v1/admin/instructor-applications")
  public Object list() {
    return jdbc.queryForList(
        "SELECT id,user_id AS userId,full_name AS"
            + " fullName,email,phone,expertise,message,status,created_at AS createdAt FROM"
            + " instructor_applications ORDER BY CASE status WHEN 'PENDING' THEN 0 WHEN 'REVIEWED' THEN 1 ELSE 2 END,id DESC LIMIT 100");
  }

  @PatchMapping("/api/v1/admin/instructor-applications/{id}/reviewed")
  @Transactional
  public Map<String, String> reviewed(@PathVariable long id) {
    if (jdbc.update("UPDATE instructor_applications SET status='REVIEWED' WHERE id=? AND status='PENDING'", id) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return Map.of(
        "message",
        "Đã đánh dấu hồ sơ đã xem. Cấp quyền giảng viên tại mục Người dùng khi phù hợp.");
  }

  @PatchMapping("/api/v1/admin/instructor-applications/{id}/approve")
  @Transactional
  public Map<String,Object> approve(@PathVariable long id) {
    var rows=jdbc.queryForList("SELECT user_id,full_name,status FROM instructor_applications WHERE id=? FOR UPDATE",id);
    if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ.");
    var application=rows.getFirst();
    if("APPROVED".equals(application.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Hồ sơ này đã được duyệt và cấp quyền rồi.");
    var result=instructors.create(new com.edushop.dto.admin.CreateInstructorRequest(
        jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,application.get("user_id")),
        (String)application.get("full_name")));
    jdbc.update("UPDATE instructor_applications SET status='APPROVED' WHERE id=?",id);
    jdbc.update("INSERT INTO notifications(user_id,title,body,type,action_url) VALUES(?,'Hồ sơ giảng viên được duyệt','Quản trị viên đã cấp quyền giảng viên. Mở không gian giảng viên để bắt đầu.','INSTRUCTOR_APPLICATION_STATUS','/instructor')",application.get("user_id"));
    return Map.of("message","Đã duyệt hồ sơ và cấp quyền giảng viên.","instructor",result);
  }
}
