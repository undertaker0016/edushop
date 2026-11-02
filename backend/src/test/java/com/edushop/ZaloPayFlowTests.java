package com.edushop;

import static org.assertj.core.api.Assertions.*;

import com.edushop.controller.ZaloPayController;
import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZaloPayFlowTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired CheckoutService checkout;
  @Autowired PaymentService payments;
  final JsonMapper mapper = JsonMapper.builder().build();
  long user, order, payment;
  String ref;
  final Clock clock = Clock.systemUTC();
  final AtomicInteger queries = new AtomicInteger();

  ZaloPayGateway gateway(boolean shared, int resultCode) {
    return new ZaloPayGateway(
        123,
        "key1",
        "key2",
        "http://localhost:5173/payment/zalopay-return",
        "",
        shared,
        mapper,
        clock) {
      @Override
      public JsonNode query(String value) {
        assertThat(value).isEqualTo(ref);
        queries.incrementAndGet();
        return mapper.valueToTree(
            Map.of("return_code", resultCode, "amount", 100000, "zp_trans_id", 123456));
      }
    };
  }

  @BeforeEach
  void setup() {
    String unique = "zlp-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES('ZaloPay test',?,'unused')",
        unique + "@example.test");
    user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", Long.class, unique + "@example.test");
    jdbc.update("INSERT INTO instructors(user_id,full_name) VALUES(?,'Teacher')", user);
    long teacher =
        jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, user);
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,status) VALUES(?,'ZLP"
            + " course',?,'PUBLISHED')",
        user,
        unique);
    long course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, unique);
    jdbc.update(
        "INSERT INTO"
            + " course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status,registration_closes_at)"
            + " VALUES(?,?,?,CURRENT_DATE+INTERVAL 20 DAY,CURRENT_DATE+INTERVAL 30"
            + " DAY,'19h',10,100000,'OPEN',CURRENT_TIMESTAMP+INTERVAL 10 DAY)",
        course,
        teacher,
        unique);
    long offering =
        jdbc.queryForObject(
            "SELECT id FROM course_offerings WHERE offering_code=?", Long.class, unique);
    order =
        checkout
            .checkout(
                user, "checkout-" + UUID.randomUUID(), new CheckoutRequest(List.of(offering), null))
            .id();
    payment = payments.initiate(user, false, order, "attempt-" + UUID.randomUUID()).id();
    ref = gateway(false, 3).reference("ref-" + UUID.randomUUID(), clock.instant());
    jdbc.update(
        "UPDATE payments SET"
            + " provider='ZALOPAY_SANDBOX',provider_order_ref=?,provider_transaction_id=NULL,checkout_url=NULL"
            + " WHERE id=?",
        ref,
        payment);
  }

  JsonNode callback(long amount) {
    String raw =
        mapper.writeValueAsString(
            Map.of("app_id", 123, "app_trans_id", ref, "amount", amount, "zp_trans_id", 123456));
    return mapper.valueToTree(Map.of("type", 1, "data", raw, "mac", MomoGateway.hmac("key2", raw)));
  }

  Jwt actor(long id) {
    return Jwt.withTokenValue("test").header("alg", "HS256").subject("" + id).build();
  }

  long enrollmentCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM enrollments WHERE user_id=?", Long.class, user);
  }

  @Test
  void privateCallbackGrantsExactlyOnceAndPublicDemoCannotUseIt() {
    var privateController = new ZaloPayController(gateway(false, 3), payments);
    assertThat(
            new ZaloPayController(gateway(true, 3), payments)
                .callback(callback(100000))
                .get("return_code"))
        .isEqualTo(2);
    assertThat(enrollmentCount()).isZero();
    assertThat(privateController.callback(callback(100000)).get("return_code")).isEqualTo(1);
    assertThat(privateController.callback(callback(100000)).get("return_code")).isEqualTo(1);
    assertThat(enrollmentCount()).isEqualTo(1);
  }

  @Test
  void verifiedWrongAmountIsRejectedWithoutGrantingAccess() {
    assertThat(
            new ZaloPayController(gateway(false, 3), payments)
                .callback(callback(200000))
                .get("return_code"))
        .isEqualTo(2);
    assertThat(enrollmentCount()).isZero();
    assertThat(payments.payment(payment).get("status")).isEqualTo("PENDING");
  }

  @Test
  void ownerQueryCanFinalizeSharedDemoButAnotherUserCannotTriggerQuery() {
    var controller = new ZaloPayController(gateway(true, 1), payments);
    assertThatThrownBy(() -> controller.verify(actor(user + 1000000), payment))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(queries.get()).isZero();
    assertThat(controller.verify(actor(user), payment).status()).isEqualTo("SUCCEEDED");
    controller.verify(actor(user), payment);
    assertThat(queries.get()).isEqualTo(1);
    assertThat(enrollmentCount()).isEqualTo(1);
  }

  @Test
  void pendingProviderResultNeverBecomesPaidAndQueriesAreThrottled() {
    var controller = new ZaloPayController(gateway(true, 3), payments);
    assertThat(controller.verify(actor(user), payment).status()).isEqualTo("PENDING");
    controller.verify(actor(user), payment);
    assertThat(queries.get()).isEqualTo(1);
    assertThat(enrollmentCount()).isZero();
  }

  @Test
  void lateQuerySuccessRequiresReconciliation() {
    jdbc.update(
        "UPDATE orders SET expires_at=CURRENT_TIMESTAMP-INTERVAL 1 MINUTE WHERE id=?", order);
    new ZaloPayController(gateway(true, 1), payments).verify(actor(user), payment);
    assertThat(enrollmentCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT reconciliation_required FROM payments WHERE id=?", Boolean.class, payment))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order))
        .isEqualTo("EXPIRED");
  }
}
