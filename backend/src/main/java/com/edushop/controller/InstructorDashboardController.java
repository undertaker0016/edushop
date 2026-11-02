package com.edushop.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
@RestController @RequestMapping("/api/v1/instructor/dashboard")
@Tag(name="Instructor and Offerings") @SecurityRequirement(name="bearerAuth")
public class InstructorDashboardController {
    private final JdbcTemplate jdbc;
    public InstructorDashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping
    public Map<String,Object> dashboard(@AuthenticationPrincipal Jwt jwt){
        long user=Long.parseLong(jwt.getSubject());
        return Map.of("coursesByStatus",jdbc.queryForList("SELECT status,COUNT(*) AS count FROM courses WHERE created_by_user_id=? GROUP BY status",user),
            "offeringsByStatus",jdbc.queryForList("SELECT o.status,COUNT(*) AS count FROM course_offerings o JOIN courses c ON c.id=o.course_id WHERE c.created_by_user_id=? GROUP BY o.status",user),
            "activeEnrollments",jdbc.queryForObject("SELECT COUNT(*) FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id JOIN courses c ON c.id=o.course_id WHERE c.created_by_user_id=? AND e.status='ACTIVE'",Long.class,user));
    }
}
