package com.edushop.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class PublicInstructorController {
    private final JdbcTemplate jdbc;
    public PublicInstructorController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PublicInstructor(Long id, String fullName, String bio, String avatarUrl,
        String contactEmail,String phone,String zalo,String facebookUrl) {}

    @GetMapping("/{slug}/instructors")
    public List<PublicInstructor> list(@PathVariable String slug) {
        return jdbc.query("""
            SELECT DISTINCT i.id, i.full_name, i.bio, i.avatar_url,i.contact_email,i.phone,i.zalo,i.facebook_url
            FROM instructors i JOIN course_offerings o ON o.instructor_id=i.id
            JOIN courses c ON c.id=o.course_id
            WHERE c.slug=? AND c.status='PUBLISHED' AND o.status='OPEN'
            ORDER BY i.full_name
            """, (rs, row) -> new PublicInstructor(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8)), slug);
    }
}
