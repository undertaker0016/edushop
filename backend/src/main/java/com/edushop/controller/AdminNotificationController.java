package com.edushop.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
 private final JdbcTemplate jdbc;
 public AdminNotificationController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public record Create(@NotNull @Positive Long userId, @NotBlank @Size(max=200) String title, @NotBlank @Size(max=10000) String body) {}
 @PostMapping
 @Transactional
 public Map<String,Integer> create(@Valid @RequestBody Create request) {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Integer.class,request.userId())==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");
  jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES (?,?,?,'ADMIN_MESSAGE')",request.userId(),request.title().trim(),request.body().trim());
  return Map.of("created",1);
 }
}
