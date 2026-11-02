package com.edushop.service;

import com.edushop.dto.notifications.CampaignRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class NotificationCampaignService {
    private final JdbcTemplate jdbc;
    public NotificationCampaignService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private boolean manager(long sender) {
        List<String> roles = jdbc.queryForList("SELECT r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE ur.user_id=? AND u.status='ACTIVE'", String.class, sender);
        if (!roles.contains("ADMIN") && !roles.contains("INSTRUCTOR")) throw error(HttpStatus.FORBIDDEN, "Only active administrators or instructors may send campaigns");
        return roles.contains("ADMIN");
    }

    private Audience audience(long sender, CampaignRequest request) {
        boolean admin = manager(sender);
        String target = request.audience();
        if (!admin && !"OFFERING_ACTIVE".equals(target)) throw error(HttpStatus.FORBIDDEN, "Instructors may only contact their assigned offering");
        boolean course = target.startsWith("COURSE_");
        boolean offering = target.startsWith("OFFERING_");
        if ((course && request.courseId() == null) || (offering && request.offeringId() == null)
                || (!course && request.courseId() != null) || (!offering && request.offeringId() != null)) {
            throw error(HttpStatus.BAD_REQUEST, "Select exactly the resource required by the audience");
        }
        if (course && jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE id=?", Long.class, request.courseId()) == 0) throw error(HttpStatus.NOT_FOUND, "Course not found");
        if (offering) {
            Long assigned = jdbc.queryForObject("SELECT COUNT(*) FROM course_offerings o JOIN instructors i ON i.id=o.instructor_id WHERE o.id=? AND (? OR i.user_id=?)", Long.class, request.offeringId(), admin, sender);
            if (assigned == 0) throw error(HttpStatus.NOT_FOUND, "Assigned offering not found");
        }
        String base = "SELECT u.id,u.full_name FROM users u WHERE u.status='ACTIVE'";
        List<Object> args = new ArrayList<>();
        String condition = switch (target) {
            case "ALL_ACTIVE" -> "";
            // Buyer is an ordinary account, not an application role.
            case "BUYERS" -> " AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id)";
            case "INSTRUCTORS" -> " AND EXISTS (SELECT 1 FROM instructors i JOIN user_roles ur ON ur.user_id=i.user_id JOIN roles r ON r.id=ur.role_id WHERE i.user_id=u.id AND r.name='INSTRUCTOR')";
            case "COURSE_ACTIVE", "OFFERING_ACTIVE" -> {
                args.add(course ? request.courseId() : request.offeringId());
                yield " AND EXISTS (SELECT 1 FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id WHERE e.user_id=u.id AND e.status='ACTIVE' AND (e.expires_at IS NULL OR e.expires_at>CURRENT_TIMESTAMP(6)) AND " + (course ? "o.course_id" : "o.id") + "=?)";
            }
            case "COURSE_PAID", "OFFERING_PAID" -> {
                args.add(course ? request.courseId() : request.offeringId());
                yield " AND EXISTS (SELECT 1 FROM orders ord JOIN order_items oi ON oi.order_id=ord.id JOIN course_offerings o ON o.id=oi.offering_id WHERE ord.user_id=u.id AND ord.status='PAID' AND " + (course ? "o.course_id" : "o.id") + "=?)";
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unsupported audience");
        };
        return new Audience(base + condition, args.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String,Object> preview(long sender, CampaignRequest request) {
        Audience a = audience(sender, request);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM (" + a.sql + ") recipients", Long.class, a.args);
        // Names only: no recipient emails, phones, or arbitrary user records.
        var sample = jdbc.query(a.sql + " ORDER BY u.id LIMIT 5", (rs, n) -> Map.of("name", rs.getString("full_name")), a.args);
        return Map.of("count", count, "sample", sample);
    }

    @Transactional
    public Map<String,Object> send(long sender, String key, CampaignRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) throw error(HttpStatus.BAD_REQUEST, "Valid Idempotency-Key required");
        // Serialize sends by sender so concurrent retries never create duplicate campaigns.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, sender);
        manager(sender);
        var existing = jdbc.queryForList("SELECT * FROM notification_campaigns WHERE sender_user_id=? AND idempotency_key=?", sender, key);
        if (!existing.isEmpty()) {
            var saved = existing.getFirst();
            if (!Objects.equals(saved.get("title"), request.title()) || !Objects.equals(saved.get("body"), request.body())
                    || !Objects.equals(saved.get("type"), request.type()) || !Objects.equals(saved.get("audience"), request.audience())
                    || !sameId(saved.get("course_id"), request.courseId()) || !sameId(saved.get("offering_id"), request.offeringId())) {
                throw error(HttpStatus.CONFLICT, "Idempotency key already used for different content");
            }
            return saved;
        }
        Audience a = audience(sender, request);
        jdbc.update("INSERT INTO notification_campaigns(sender_user_id,idempotency_key,title,body,type,audience,course_id,offering_id) VALUES(?,?,?,?,?,?,?,?)",
                sender,key,request.title(),request.body(),request.type(),request.audience(),request.courseId(),request.offeringId());
        long id = jdbc.queryForObject("SELECT id FROM notification_campaigns WHERE sender_user_id=? AND idempotency_key=?", Long.class, sender,key);
        List<Object> args = new ArrayList<>(); args.add(id); args.addAll(Arrays.asList(a.args));
        // Snapshot and fan-out are set-based; recipient lists are not loaded into Java memory.
        int count = jdbc.update("INSERT INTO notification_deliveries(campaign_id,user_id) SELECT ?,recipients.id FROM (" + a.sql + ") recipients", args.toArray());
        if (count == 0) throw error(HttpStatus.CONFLICT, "No eligible recipients; campaign was not sent");
        jdbc.update("INSERT INTO notifications(user_id,title,body,type,action_url,campaign_id) SELECT user_id,?,?,?, ?,campaign_id FROM notification_deliveries WHERE campaign_id=?", request.title(),request.body(),request.type(),request.offeringId()==null?"/buyer/notifications":"/buyer/classroom",id);
        jdbc.update("UPDATE notification_campaigns SET recipient_count=?,status='SENT',sent_at=CURRENT_TIMESTAMP(6) WHERE id=?", count,id);
        return jdbc.queryForMap("SELECT * FROM notification_campaigns WHERE id=?",id);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> history(long sender, int page, String audience, String type) {
        boolean admin = manager(sender);
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Invalid page");
        String where = " WHERE (? OR sender_user_id=?) AND (?='' OR audience=?) AND (?='' OR type=?)";
        Object[] filters = {admin,sender,audience,audience,type,type};
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM notification_campaigns"+where,Long.class,filters);
        var args = new ArrayList<>(Arrays.asList(filters)); args.add((long)page*20);
        return Map.of("items",jdbc.queryForList("SELECT id,title,type,audience,status,recipient_count,created_at,sent_at FROM notification_campaigns"+where+" ORDER BY id DESC LIMIT 20 OFFSET ?",args.toArray()),"totalPages",(count+19)/20,"totalItems",count);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> options(long sender, String kind, String query) {
        boolean admin = manager(sender);
        if ("offerings".equals(kind)) return jdbc.queryForList("SELECT o.id,CONCAT(o.offering_code,' — ',c.title) AS name FROM course_offerings o JOIN courses c ON c.id=o.course_id JOIN instructors i ON i.id=o.instructor_id WHERE (? OR i.user_id=?) AND (o.offering_code LIKE ? OR c.title LIKE ?) ORDER BY o.id DESC LIMIT 30",admin,sender,"%"+query+"%","%"+query+"%");
        if (admin && "courses".equals(kind)) return jdbc.queryForList("SELECT id,title AS name FROM courses WHERE title LIKE ? ORDER BY id DESC LIMIT 30","%"+query+"%");
        throw error(HttpStatus.FORBIDDEN,"Resource selection is not allowed");
    }

    private static boolean sameId(Object saved, Long value) { return saved == null ? value == null : value != null && ((Number)saved).longValue()==value; }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    private record Audience(String sql,Object[] args) {}
}
