package com.edushop.service;

import com.edushop.dto.enrollment.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnrollmentService {
    private final JdbcTemplate jdbc;
    public EnrollmentService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public List<EnrollmentResponse> list(long userId){
        return jdbc.query("""
            SELECT e.id,e.user_id,u.full_name learner_name,u.email learner_email,
                   e.offering_id,c.title course_title,o.offering_code,o.start_date,o.end_date,
                   e.status,e.enrolled_at
            FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id
            JOIN courses c ON c.id=o.course_id JOIN users u ON u.id=e.user_id
            WHERE e.user_id=? ORDER BY e.enrolled_at DESC
            """,this::map,userId);
    }

    @Transactional(readOnly=true)
    public List<EnrollmentResponse> listForAdmin(int page,int size,String status){
        if(page<0||size<1||size>100) throw bad("Invalid page or size");
        if(status!=null&&!status.isBlank()&&!List.of("ACTIVE","EXPIRED","CANCELLED").contains(status))
            throw bad("Enrollment status is invalid");
        String s=status==null||status.isBlank()?null:status;
        return jdbc.query("""
            SELECT e.id,e.user_id,u.full_name learner_name,u.email learner_email,
                   e.offering_id,c.title course_title,o.offering_code,o.start_date,o.end_date,
                   e.status,e.enrolled_at
            FROM enrollments e JOIN course_offerings o ON o.id=e.offering_id
            JOIN courses c ON c.id=o.course_id JOIN users u ON u.id=e.user_id
            WHERE (? IS NULL OR e.status=?) ORDER BY e.enrolled_at DESC LIMIT ? OFFSET ?
            """,this::map,s,s,size,(long)page*size);
    }

    @Transactional(readOnly=true)
    public List<ClassroomSessionResponse> sessions(long userId,long enrollmentId){
        Integer allowed=jdbc.queryForObject("""
            SELECT COUNT(*) FROM enrollments e JOIN order_items oi ON oi.id=e.order_item_id
            JOIN orders ord ON ord.id=oi.order_id JOIN course_offerings o ON o.id=e.offering_id
            WHERE e.id=? AND e.user_id=? AND e.status='ACTIVE'
              AND (e.expires_at IS NULL OR e.expires_at>CURRENT_TIMESTAMP(6))
              AND ord.user_id=e.user_id AND ord.status='PAID'
              AND oi.offering_id=e.offering_id AND o.status<>'CANCELLED'
            """,Integer.class,enrollmentId,userId);
        if(allowed==null||allowed==0)throw notFound("Enrollment not found");
        return jdbc.query("""
            SELECT session_number,title,starts_at,ends_at,meeting_provider,meeting_url,
                   meeting_password,notes,status
            FROM class_sessions
            WHERE offering_id=(SELECT offering_id FROM enrollments WHERE id=? AND user_id=?)
              AND status<>'CANCELLED' ORDER BY starts_at
            """,(rs,row)->new ClassroomSessionResponse(rs.getShort("session_number"),rs.getString("title"),
                local(rs.getTimestamp("starts_at")),local(rs.getTimestamp("ends_at")),
                rs.getString("meeting_provider"),rs.getString("meeting_url"),rs.getString("meeting_password"),
                rs.getString("notes"),rs.getString("status")),enrollmentId,userId);
    }

    @Transactional
    public void cancelByAdmin(long enrollmentId){
        if(jdbc.update("UPDATE enrollments SET status='CANCELLED' WHERE id=? AND status='ACTIVE'",enrollmentId)==0)
            throw notFound("Active enrollment not found");
        jdbc.update("""
            INSERT INTO notifications(user_id,title,body,type)
            SELECT user_id,'Enrollment bị hủy',CONCAT('Quyền truy cập enrollment #',id,' đã bị quản trị viên hủy.'),
                   'ENROLLMENT_CANCELLED' FROM enrollments WHERE id=?
            """,enrollmentId);
    }

    private EnrollmentResponse map(ResultSet rs,int row)throws SQLException{
        return new EnrollmentResponse(rs.getLong("id"),rs.getLong("user_id"),rs.getString("learner_name"),
            rs.getString("learner_email"),rs.getLong("offering_id"),rs.getString("course_title"),
            rs.getString("offering_code"),rs.getDate("start_date").toLocalDate(),rs.getDate("end_date").toLocalDate(),
            rs.getString("status"),rs.getTimestamp("enrolled_at").toLocalDateTime());
    }
    private static LocalDateTime local(Timestamp t){return t==null?null:t.toLocalDateTime();}
    private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
