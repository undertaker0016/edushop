package com.edushop;

import com.edushop.dto.notifications.CampaignRequest;
import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.service.CheckoutService;
import com.edushop.service.NotificationCampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NotificationCampaignTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationCampaignService campaigns;
    @Autowired CheckoutService checkout;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    String token(long id) {
        Instant now=Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
            .issuer("edushop-test").subject(""+id).issuedAt(now).expiresAt(now.plusSeconds(900)).build())).getTokenValue();
    }

    @Test void httpSecurityValidationAndPersonalReadIsolation() throws Exception {
        long owner=user(null),other=user(null),admin=user("ADMIN");
        jdbc.update("INSERT INTO notifications(user_id,title,body,type) VALUES(?,'Private','Private content','ANNOUNCEMENT')",owner);
        long id=jdbc.queryForObject("SELECT MAX(id) FROM notifications WHERE user_id=?",Long.class,owner);
        mvc.perform(get("/api/v1/notifications/center")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/center?read=false&type=ANNOUNCEMENT").header("Authorization","Bearer "+token(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/notifications/center").header("Authorization","Bearer "+token(other)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(patch("/api/v1/notifications/"+id+"/read").header("Authorization","Bearer "+token(other))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/read-all").header("Authorization","Bearer "+token(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications/center?read=false").header("Authorization","Bearer "+token(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(post("/api/v1/admin/notification-campaigns/preview").header("Authorization","Bearer "+token(owner)).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notification-campaigns/preview").header("Authorization","Bearer "+token(admin)).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    long user(String role) {
        String email = "campaign-"+UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO users(full_name,email,password_hash) VALUES('Campaign Test',?,'not-a-password')",email);
        long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        if(role!=null) jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name=?",id,role);
        return id;
    }
    long offering(long teacher) {
        jdbc.update("INSERT INTO instructors(user_id,full_name) VALUES(?,'Campaign Teacher')",teacher);
        long instructor=jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?",Long.class,teacher);
        String slug="campaign-"+UUID.randomUUID();
        jdbc.update("INSERT INTO courses(created_by_user_id,title,slug,status) VALUES(?,'Campaign Course',?,'PUBLISHED')",teacher,slug);
        long course=jdbc.queryForObject("SELECT id FROM courses WHERE slug=?",Long.class,slug);
        jdbc.update("INSERT INTO course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status) VALUES(?,?,?,CURRENT_DATE+INTERVAL 10 DAY,CURRENT_DATE+INTERVAL 20 DAY,'Weekly',20,0,'OPEN')",course,instructor,slug);
        return jdbc.queryForObject("SELECT id FROM course_offerings WHERE offering_code=?",Long.class,slug);
    }
    CampaignRequest request(long offering) { return new CampaignRequest("Lịch học", "Nội dung", "ANNOUNCEMENT", "OFFERING_ACTIVE",null,offering); }

    @Test void snapshotsDeduplicateExcludeBannedAndRetrySafely() {
        long admin=user("ADMIN"), teacher=user("INSTRUCTOR"), learner=user(null), banned=user(null), outsider=user(null);
        long offering=offering(teacher);
        checkout.checkout(learner,"free-"+UUID.randomUUID(),new CheckoutRequest(List.of(offering),null));
        checkout.checkout(banned,"free-"+UUID.randomUUID(),new CheckoutRequest(List.of(offering),null));
        jdbc.update("UPDATE users SET status='BANNED' WHERE id=?",banned);
        assertThat(campaigns.preview(admin,request(offering)).get("count")).isEqualTo(1L);
        String key="send-"+UUID.randomUUID();
        var sent=campaigns.send(admin,key,request(offering));
        assertThat(campaigns.send(admin,key,request(offering)).get("id")).isEqualTo(sent.get("id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE campaign_id=? AND user_id=?",Long.class,sent.get("id"),learner)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE campaign_id=? AND user_id IN (?,?)",Long.class,sent.get("id"),outsider,banned)).isZero();
        assertThatThrownBy(()->campaigns.send(admin,key,new CampaignRequest("Changed","Nội dung","ANNOUNCEMENT","OFFERING_ACTIVE",null,offering)))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test void instructorMayOnlyTargetAssignedOfferingAndBuyerCannotSend() {
        long teacher=user("INSTRUCTOR"),other=user("INSTRUCTOR"),buyer=user(null);
        long own=offering(teacher),foreign=offering(other);
        assertThat(campaigns.preview(teacher,request(own)).get("count")).isEqualTo(0L);
        assertThatThrownBy(()->campaigns.preview(teacher,request(foreign))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(()->campaigns.preview(teacher,new CampaignRequest("x","y","SYSTEM","ALL_ACTIVE",null,null))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->campaigns.preview(buyer,request(own))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(campaigns.options(teacher,"offerings", "Campaign")).allMatch(row -> ((Number)row.get("id")).longValue()==own);
    }
}
