package com.edushop.service;

import com.edushop.dto.admin.AdminPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly=true)
public class AdminReadService {
    private final JdbcTemplate jdbc;
    public AdminReadService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public AdminPage list(String module, int page, int size, String q, String status,
            Long userId, Long offeringId, Long courseId, String provider, LocalDateTime from, LocalDateTime to) {
        if(page<0 || size<1 || size>100 || (from!=null && to!=null && from.isAfter(to)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page, size or time range");
        String select, table, search, state, date;
        switch(module) {
            case "users" -> { select="u.id,u.full_name AS fullName,u.email,u.phone,u.status,u.created_at AS createdAt, (SELECT GROUP_CONCAT(r.name ORDER BY r.name) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id) AS roles"; table="users u"; search="CONCAT(u.email,' ',u.full_name)";state="u.status";date="u.created_at"; }
            case "instructors" -> { select="i.id,i.user_id AS userId,i.full_name AS fullName,i.bio,i.avatar_url AS avatarUrl,i.contact_email AS contactEmail,i.phone,i.zalo,i.facebook_url AS facebookUrl,u.email,u.status,EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.name='INSTRUCTOR') AS hasInstructorRole";table="instructors i JOIN users u ON u.id=i.user_id"; search="CONCAT(i.full_name,' ',u.email)";state="u.status";date="i.created_at"; }
            case "courses" -> { select="c.id,c.created_by_user_id AS creatorId,c.title,c.slug,c.description,c.thumbnail_url AS thumbnailUrl,c.status,c.created_at AS createdAt";table="courses c";search="CONCAT(c.title,' ',c.slug)";state="c.status";date="c.created_at";}
            case "offerings" -> { select="o.id,o.course_id AS courseId,o.instructor_id AS instructorId,o.offering_code AS code,o.cohort_name AS cohortName,o.start_date AS startDate,o.end_date AS endDate,o.registration_closes_at AS registrationClosesAt,o.schedule_summary AS scheduleSummary,o.price,o.currency,o.capacity,o.status";table="course_offerings o";search="o.offering_code";state="o.status";date="o.created_at";}
            case "sessions" -> { select="s.id,s.offering_id AS offeringId,o.offering_code AS offeringCode,s.session_number AS sessionNumber,s.title,s.starts_at AS startsAt,s.ends_at AS endsAt,s.status,s.meeting_url AS meetingUrl";table="class_sessions s JOIN course_offerings o ON o.id=s.offering_id";search="CONCAT(s.title,' ',o.offering_code)";state="s.status";date="s.starts_at";}
            case "orders" -> { select="o.id,o.order_number AS orderNumber,o.user_id AS userId,u.full_name AS buyerName,u.email AS buyerEmail,o.subtotal,o.discount_amount AS discountAmount,o.total_amount AS totalAmount,o.currency,o.status,o.created_at AS createdAt,o.expires_at AS expiresAt,o.paid_at AS paidAt";table="orders o JOIN users u ON u.id=o.user_id";search="o.order_number";state="o.status";date="o.created_at";}
            case "payments" -> {select="p.id,p.order_id AS orderId,p.provider,p.provider_transaction_id AS providerTransactionId,p.provider_order_ref AS providerOrderRef,p.amount,p.currency,p.status,p.created_at AS createdAt,p.paid_at AS paidAt,p.reconciliation_required AS reconciliationRequired,p.result_note AS resultNote";table="payments p";search="COALESCE(p.provider_order_ref,'')";state="p.status";date="p.created_at";}
            case "enrollments" -> {select="e.id,e.user_id AS userId,u.full_name AS learnerName,u.email AS learnerEmail,e.offering_id AS offeringId,e.order_item_id AS orderItemId,oi.order_id AS orderId,e.status,e.enrolled_at AS enrolledAt,e.expires_at AS expiresAt";table="enrollments e JOIN users u ON u.id=e.user_id LEFT JOIN order_items oi ON oi.id=e.order_item_id";search="CONCAT(u.full_name,' ',u.email)";state="e.status";date="e.created_at";}
            case "reviews" -> {select="r.id,r.user_id AS userId,r.course_id AS courseId,r.rating,r.comment,r.is_hidden AS hidden,r.created_at AS createdAt";table="reviews r";search="COALESCE(r.comment,'')";state=null;date="r.created_at";}
            case "categories" -> {select="c.id,c.slug,c.name,c.icon,c.sort_order AS sortOrder,c.is_active AS active";table="categories c";search="CONCAT(c.name,' ',c.slug)";state=null;date=null;}
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Object> args=new ArrayList<>();
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        if(q!=null && !q.isBlank()) {where.append(" AND ").append(search).append(" LIKE ?");args.add("%"+q+"%");}
        if(status!=null && !status.isBlank() && state!=null) {where.append(" AND ").append(state).append("=?");args.add(status);}
        if(from!=null && date!=null){where.append(" AND ").append(date).append(">=?");args.add(from);}
        if(to!=null && date!=null){where.append(" AND ").append(date).append("<?");args.add(to);}
        if(userId!=null) {
            String col=switch(module){case "orders"->"o.user_id";case "enrollments"->"e.user_id";case "courses"->"c.created_by_user_id";case "instructors"->"i.user_id";case "reviews"->"r.user_id";default->null;};
            if(col!=null){where.append(" AND ").append(col).append("=?");args.add(userId);}
        }
        if(offeringId!=null && module.equals("enrollments")){where.append(" AND e.offering_id=?");args.add(offeringId);}
        if(courseId!=null && module.equals("offerings")){where.append(" AND o.course_id=?");args.add(courseId);}
        if(provider!=null && module.equals("payments")){where.append(" AND p.provider=?");args.add(provider);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+where,Long.class,args.toArray());
        String sort=module.equals("categories")?"sortOrder, id":"id DESC";
        args.add(size);args.add((long)page*size);
        return new AdminPage(jdbc.queryForList("SELECT "+select+" FROM "+table+where+" ORDER BY "+sort+" LIMIT ? OFFSET ?",args.toArray()),page,size,total,(total+size-1)/size);
    }

    public Map<String,Object> detail(String module,long id) {
        // Reuse the projection without exposing all columns.
        String columns=switch(module){
            case "users" -> "id,full_name AS fullName,email,phone,status,created_at AS createdAt,(SELECT GROUP_CONCAT(r.name ORDER BY r.name) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=users.id) AS roles";
            case "payments" -> "id,order_id AS orderId,provider,provider_transaction_id AS providerTransactionId,provider_order_ref AS providerOrderRef,amount,currency,status,reconciliation_required AS reconciliationRequired,result_note AS resultNote";
            case "enrollments" -> "id,user_id AS userId,offering_id AS offeringId,order_item_id AS orderItemId,status,enrolled_at AS enrolledAt,expires_at AS expiresAt";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        var rows=jdbc.queryForList("SELECT "+columns+" FROM "+module+" WHERE id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    public Map<String,Object> dashboard(LocalDateTime from,LocalDateTime to) {
        if(from!=null && to!=null && from.isAfter(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("users",count("users"));data.put("instructors",count("instructors"));
        data.put("coursesByStatus",jdbc.queryForList("SELECT status,COUNT(*) AS count FROM courses GROUP BY status"));
        data.put("openOfferings",jdbc.queryForObject("SELECT COUNT(*) FROM course_offerings WHERE status='OPEN'",Long.class));
        data.put("ordersByStatus",jdbc.queryForList("SELECT status,COUNT(*) AS count FROM orders WHERE (? IS NULL OR created_at>=?) AND (? IS NULL OR created_at<?) GROUP BY status",from,from,to,to));
        data.put("activeEnrollments",jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE status='ACTIVE'",Long.class));
        data.put("paymentsByProvider",jdbc.queryForList("SELECT provider,COUNT(*) AS succeeded,SUM(amount) AS amount FROM payments WHERE status='SUCCEEDED' AND reconciliation_required=FALSE AND (? IS NULL OR paid_at>=?) AND (? IS NULL OR paid_at<?) GROUP BY provider",from,from,to,to));
        data.put("productionRevenue",jdbc.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM orders o WHERE o.status='PAID' AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status='SUCCEEDED' AND p.provider='MOMO_PRODUCTION' AND p.reconciliation_required=FALSE) AND (? IS NULL OR o.paid_at>=?) AND (? IS NULL OR o.paid_at<?)",java.math.BigDecimal.class,from,from,to,to));
        data.put("popularOfferings",jdbc.queryForList("SELECT o.id,o.offering_code AS code,COUNT(*) AS enrollments FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id WHERE e.status='ACTIVE' GROUP BY o.id,o.offering_code ORDER BY enrollments DESC LIMIT 10"));
        return data;
    }
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Transactional
    public void moderate(long id,boolean hidden) {
        if(jdbc.update("UPDATE reviews SET is_hidden=? WHERE id=?",hidden,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found");
    }
}

