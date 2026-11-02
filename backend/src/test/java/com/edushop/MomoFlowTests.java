package com.edushop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.edushop.controller.MomoController;
import com.edushop.dto.orders.CheckoutRequest;
import com.edushop.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class MomoFlowTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired CheckoutService checkout;
  @Autowired PaymentService payments;
  @Autowired MockMvc mvc;
  final JsonMapper mapper = JsonMapper.builder().build();
  MockMvc ipn;
  long user;

  record Attempt(long order, long payment, String ref) {}

  @BeforeEach
  void setup() {
    String email = UUID.randomUUID() + "@momo-flow.test";
    jdbc.update(
        "INSERT INTO users(full_name,email,password_hash) VALUES('MoMo flow',?,'unused')", email);
    user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    var gateway =
        new MomoGateway(
            "MOMO_SANDBOX",
            "TEST_PARTNER",
            "test-access",
            "test-secret",
            "http://localhost:5173/payment/momo-return",
            "https://example.test/ipn",
            false,
            mapper);
    ipn = MockMvcBuilders.standaloneSetup(new MomoController(gateway, payments)).build();
  }

  Attempt attempt(boolean momo) {
    String slug = "momo-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO instructors(user_id,full_name) SELECT ?,'Teacher' WHERE NOT EXISTS(SELECT 1"
            + " FROM instructors WHERE user_id=?)",
        user,
        user);
    long teacher =
        jdbc.queryForObject("SELECT id FROM instructors WHERE user_id=?", Long.class, user);
    jdbc.update(
        "INSERT INTO courses(created_by_user_id,title,slug,status)"
            + " VALUES(?,'Course',?,'PUBLISHED')",
        user,
        slug);
    long course = jdbc.queryForObject("SELECT id FROM courses WHERE slug=?", Long.class, slug);
    jdbc.update(
        "INSERT INTO"
            + " course_offerings(course_id,instructor_id,offering_code,start_date,end_date,schedule_summary,capacity,price,status,registration_closes_at)"
            + " VALUES(?,?,?,CURRENT_DATE+INTERVAL 20 DAY,CURRENT_DATE+INTERVAL 30"
            + " DAY,'19h',10,100000,'OPEN',CURRENT_TIMESTAMP+INTERVAL 10 DAY)",
        course,
        teacher,
        slug);
    long offering =
        jdbc.queryForObject(
            "SELECT id FROM course_offerings WHERE offering_code=?", Long.class, slug);
    var order =
        checkout.checkout(
            user, "checkout-" + UUID.randomUUID(), new CheckoutRequest(List.of(offering), null));
    var payment = payments.initiate(user, false, order.id(), "attempt-" + UUID.randomUUID());
    if (momo)
      jdbc.update(
          "UPDATE payments SET"
              + " provider='MOMO_SANDBOX',provider_transaction_id=NULL,checkout_url=NULL WHERE"
              + " id=?",
          payment.id());
    return new Attempt(
        order.id(),
        payment.id(),
        (String) payments.payment(payment.id()).get("provider_order_ref"));
  }

  String payload(Attempt a, int code, long transaction, long amount) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amount);
    body.put("extraData", "");
    body.put("message", "Test");
    body.put("orderId", a.ref());
    body.put("orderInfo", "EduShop " + a.ref());
    body.put("orderType", "momo_wallet");
    body.put("partnerCode", "TEST_PARTNER");
    body.put("payType", "qr");
    body.put("requestId", a.ref());
    body.put("responseTime", 1721720663942L);
    body.put("resultCode", code);
    body.put("transId", transaction);
    StringJoiner canonical = new StringJoiner("&");
    canonical.add("accessKey=test-access");
    body.forEach((k, v) -> canonical.add(k + "=" + v));
    body.put("signature", MomoGateway.hmac("test-secret", canonical.toString()));
    return mapper.writeValueAsString(body);
  }

  String orderStatus(Attempt a) {
    return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, a.order());
  }

  @Test
  void signedSuccessIsIdempotentAndGrantsExactlyOneEnrollment() throws Exception {
    var a = attempt(true);
    String body = payload(a, 0, 12345, 100000);
    for (int i = 0; i < 2; i++)
      ipn.perform(
              post("/api/v1/payments/webhooks/momo").contentType("application/json").content(body))
          .andExpect(status().isNoContent());
    assertThat(orderStatus(a)).isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollments e JOIN order_items i ON i.id=e.order_item_id"
                    + " WHERE i.order_id=?",
                Long.class,
                a.order()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='ORDER_PAID'",
                Long.class,
                user))
        .isEqualTo(1);
  }

  @Test
  void forgedSignatureAndSignedWrongAmountNeverPayOrder() throws Exception {
    var a = attempt(true);
    ipn.perform(
            post("/api/v1/payments/webhooks/momo")
                .contentType("application/json")
                .content(
                    payload(a, 0, 12345, 100000).replace("\"amount\":100000", "\"amount\":200000")))
        .andExpect(status().isUnauthorized());
    ipn.perform(
            post("/api/v1/payments/webhooks/momo")
                .contentType("application/json")
                .content(payload(a, 0, 12345, 200000)))
        .andExpect(status().isConflict());
    assertThat(orderStatus(a)).isEqualTo("PENDING");
  }

  @Test
  void twoCancelledAttemptsWithZeroTransactionDoNotCollide() throws Exception {
    var a = attempt(true);
    var b = attempt(true);
    for (var row : List.of(a, b))
      ipn.perform(
              post("/api/v1/payments/webhooks/momo")
                  .contentType("application/json")
                  .content(payload(row, 1006, 0, 100000)))
          .andExpect(status().isNoContent());
    assertThat(payments.payment(a.payment()).get("status")).isEqualTo("FAILED");
    assertThat(payments.payment(b.payment()).get("status")).isEqualTo("FAILED");
    assertThat(payments.payment(a.payment()).get("provider_transaction_id")).isNull();
    assertThat(orderStatus(a)).isEqualTo("PENDING");
  }

  @Test
  void resumeReusesAttemptAndResolutionIsOwnerOnly() throws Exception {
    var a = attempt(false);
    assertThat(payments.resume(user, a.payment()).id()).isEqualTo(a.payment());
    mvc.perform(
            get("/api/v1/payments/references/" + a.ref())
                .with(jwt().jwt(j -> j.subject("" + user))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderStatus").value("PENDING"));
    mvc.perform(
            get("/api/v1/payments/references/" + a.ref())
                .with(jwt().jwt(j -> j.subject("" + (user + 1000000)))))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/payments/" + a.payment() + "/resume")
                .with(jwt().jwt(j -> j.subject("" + (user + 1000000)))))
        .andExpect(status().isNotFound());
  }

  @Test
  void successfulIpnWithoutTransactionIsRejected() throws Exception {
    var a = attempt(true);
    ipn.perform(
            post("/api/v1/payments/webhooks/momo")
                .contentType("application/json")
                .content(payload(a, 0, 0, 100000)))
        .andExpect(status().isBadRequest());
    assertThat(orderStatus(a)).isEqualTo("PENDING");
  }

  @Test
  void adminQueryCanFinalizeExplicitCancellation() {
    var a = attempt(true);
    var gateway =
        new MomoGateway(
            "MOMO_SANDBOX",
            "TEST_PARTNER",
            "test-access",
            "test-secret",
            "http://localhost:5173/payment/momo-return",
            "https://example.test/ipn",
            false,
            mapper) {
          @Override
          public tools.jackson.databind.JsonNode query(String ref) {
            return mapper.valueToTree(Map.of("resultCode", 1006, "transId", 0, "amount", 100000));
          }
        };
    new MomoController(gateway, payments).reconcile(a.payment());
    assertThat(payments.payment(a.payment()).get("status")).isEqualTo("FAILED");
  }

  @Test
  void lateSignedSuccessRequiresReconciliationWithoutEnrollment() throws Exception {
    var a = attempt(true);
    jdbc.update(
        "UPDATE orders SET expires_at=CURRENT_TIMESTAMP-INTERVAL 1 MINUTE WHERE id=?", a.order());
    ipn.perform(
            post("/api/v1/payments/webhooks/momo")
                .contentType("application/json")
                .content(payload(a, 0, 12345, 100000)))
        .andExpect(status().isNoContent());
    assertThat(orderStatus(a)).isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "SELECT reconciliation_required FROM payments WHERE id=?",
                Boolean.class,
                a.payment()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE user_id=?", Long.class, user))
        .isZero();
  }
}
