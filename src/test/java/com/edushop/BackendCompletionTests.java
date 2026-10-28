package com.edushop;

import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BackendCompletionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutService checkout;
    @Autowired PaymentService payments;
    @Autowired EnrollmentService enrollments;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    long buyer(){
        String email="completion-"+UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO users(full_name,email,password_hash) VALUES('Completion Test',?,'not-a-login-password')",email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    long offering(int capacity, int price){
        long teacher=buyer();
        jdbc.update("INSERT INTO instructors(user_id,full_name) VALUES(?,'Test Teacher')",teacher);
        long instructor=jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?",Long.class,teacher);
        String slug="completion-"+UUID.randomUUID();
        jdbc.update("INSERT INTO courses(created_by_user_id,title,slug,status) VALUES(?,'Completion',?,'PUBLISHED')",teacher,slug);
        long course=jdbc.queryForObject("SELECT id FROM courses WHERE slug=?",Long.class,slug);
        jdbc.update("INSERT INTO course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status) VALUES(?,?,?,CURRENT_DATE+INTERVAL 10 DAY,CURRENT_DATE+INTERVAL 20 DAY,'Weekly',?,?,'OPEN')",course,instructor,slug,capacity,price);
        long id=jdbc.queryForObject("SELECT id FROM course_offerings WHERE offering_code=?",Long.class,slug);
        jdbc.update("INSERT INTO class_sessions(offering_id,session_number,title,starts_at,ends_at,meeting_url) VALUES(?,1,'Session',CURRENT_TIMESTAMP+INTERVAL 10 DAY,CURRENT_TIMESTAMP+INTERVAL 10 DAY+INTERVAL 1 HOUR,'https://meet.google.com/test-room')",id);
        return id;
    }
    String token(long id){
        Instant now=Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("edushop-test").subject(""+id).issuedAt(now).expiresAt(now.plusSeconds(900)).build())).getTokenValue();
    }
    @Test @Transactional
    void cancelledOrExpiredEnrollmentCannotReadClassroom() throws Exception {
        long user=buyer(),offering=offering(2,0);
        var order=checkout.checkout(user,"free-"+UUID.randomUUID(),new CheckoutRequest(List.of(offering),null));
        long e=jdbc.queryForObject("SELECT id FROM enrollments WHERE user_id=? AND offering_id=?",Long.class,user,offering);
        mvc.perform(get("/api/v1/enrollments/"+e+"/sessions").header("Authorization","Bearer "+token(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/enrollments/"+e+"/sessions").header("Authorization","Bearer "+token(buyer()))).andExpect(status().isNotFound());
        jdbc.update("UPDATE enrollments SET expires_at=CURRENT_TIMESTAMP-INTERVAL 1 DAY WHERE id=?",e);
        assertThatThrownBy(()->enrollments.sessions(user,e)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        enrollments.cancelByAdmin(e);
        assertThatThrownBy(()->enrollments.sessions(user,e)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(order.status()).isEqualTo("PAID");
    }
    @Test @Transactional
    void paymentDuplicateWrongAmountAndLateSuccessAreHandled() {
        long user=buyer(),offering=offering(2,100000);
        var order=checkout.checkout(user,"paid-"+UUID.randomUUID(),new CheckoutRequest(List.of(offering),null));
        var p=payments.initiate(user,false,order.id(),"attempt-"+UUID.randomUUID());
        assertThatThrownBy(()->payments.applyVerifiedResult(p.provider(),p.providerTransactionId(),order.id(),BigDecimal.ONE,"VND",true))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        // Expected rollback-only from inner transaction belongs in a separate test; see late test below.
    }
    @Test @Transactional
    void lateSuccessRequiresReconciliationAndDoesNotEnroll() {
        long user=buyer(),offering=offering(2,100000);
        var order=checkout.checkout(user,"late-"+UUID.randomUUID(),new CheckoutRequest(List.of(offering),null));
        var p=payments.initiate(user,false,order.id(),"attempt-"+UUID.randomUUID());
        jdbc.update("UPDATE orders SET expires_at=CURRENT_TIMESTAMP-INTERVAL 1 MINUTE WHERE id=?",order.id());
        checkout.expireStaleOrders();
        payments.applyVerifiedResult(p.provider(),p.providerTransactionId(),order.id(),new BigDecimal("100000"),"VND",true);
        payments.applyVerifiedResult(p.provider(),p.providerTransactionId(),order.id(),new BigDecimal("100000"),"VND",true);
        assertThat(jdbc.queryForObject("SELECT reconciliation_required FROM payments WHERE id=?",Boolean.class,p.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id=? AND offering_id=?",Long.class,user,offering)).isZero();
    }
    @Test @Transactional
    void missingIdempotencyHeaderIs400AndAdminCannotOwnOtherBuyerOrder() throws Exception {
        long owner=buyer(),admin=buyer(),o=offering(2,0);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ADMIN'",admin);
        var order=checkout.checkout(owner,"own-"+UUID.randomUUID(),new CheckoutRequest(List.of(o),null));
        mvc.perform(get("/api/v1/orders/"+order.id()).header("Authorization","Bearer "+token(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/orders/"+order.id()).header("Authorization","Bearer "+token(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/orders/checkout").header("Authorization","Bearer "+token(owner)).contentType("application/json")
            .content("{\"offeringIds\":["+o+"]}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization","Bearer "+token(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization","Bearer "+token(admin))).andExpect(status().isOk());
    }
    @Test
    void concurrentBuyersCannotOversellLastSeat() throws Exception {
        long o=offering(1,100000),a=buyer(),b=buyer();
        try(var pool=Executors.newFixedThreadPool(2)){
            CountDownLatch start=new CountDownLatch(1);
            var tasks=new ArrayList<Future<Boolean>>();
            for(long user:List.of(a,b))tasks.add(pool.submit(()->{
                start.await();
                try{checkout.checkout(user,"race-"+UUID.randomUUID(),new CheckoutRequest(List.of(o),null));return true;}
                catch(org.springframework.web.server.ResponseStatusException e){assertThat(e.getStatusCode().value()).isEqualTo(409);return false;}
            }));
            start.countDown();
            int successes=0;for(var f:tasks)if(f.get(30,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE oi.offering_id=? AND o.status='PENDING'",Long.class,o)).isEqualTo(1);
        }
    }
}
