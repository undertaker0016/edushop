package com.edushop;

import static org.assertj.core.api.Assertions.*;

import com.edushop.service.MomoGateway;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MomoGatewayTests {
  @Test
  void queryUsesOfficialSignatureWithoutRequestId() {
    var mapper = JsonMapper.builder().build();
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
          protected tools.jackson.databind.JsonNode post(String path, Map<String, Object> body) {
            assertThat(path).isEqualTo("/v2/gateway/api/query");
            assertThat(body.get("signature"))
                .isEqualTo(
                    MomoGateway.hmac(
                        "test-secret",
                        "accessKey=test-access&orderId=PAY-123&partnerCode=TEST_PARTNER"));
            assertThat(body.get("requestId")).isNotNull();
            return mapper.valueToTree(
                Map.of(
                    "partnerCode",
                    "TEST_PARTNER",
                    "orderId",
                    "PAY-123",
                    "requestId",
                    body.get("requestId"),
                    "resultCode",
                    7000));
          }
        };
    assertThat(gateway.query("PAY-123").path("resultCode").asInt()).isEqualTo(7000);
  }

  @Test
  void createBindsAmountAndRejectsUnexpectedCheckoutHost() {
    var mapper = JsonMapper.builder().build();
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
          protected tools.jackson.databind.JsonNode post(String path, Map<String, Object> body) {
            assertThat(body.get("amount")).isEqualTo(100000L);
            assertThat(body.get("redirectUrl"))
                .isEqualTo("http://localhost:5173/payment/momo-return");
            assertThat(body.get("signature"))
                .isEqualTo(
                    MomoGateway.hmac(
                        "test-secret",
                        "accessKey=test-access&amount=100000&extraData=&ipnUrl=https://example.test/ipn&orderId=PAY-123&orderInfo=EduShop"
                            + " PAY-123&partnerCode=TEST_PARTNER&redirectUrl=http://localhost:5173/payment/momo-return&requestId=PAY-123&requestType=captureWallet"));
            return mapper.valueToTree(
                Map.of(
                    "partnerCode",
                    "TEST_PARTNER",
                    "orderId",
                    "PAY-123",
                    "requestId",
                    "PAY-123",
                    "amount",
                    100000,
                    "resultCode",
                    0,
                    "payUrl",
                    "https://evil.test/pay"));
          }
        };
    assertThatThrownBy(
            () -> gateway.initiate("PAY-123", new java.math.BigDecimal("100000"), "VND", "ignored"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(
            () -> gateway.initiate("PAY-123", new java.math.BigDecimal("1000.5"), "VND", "ignored"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void malformedAndLocalCallbacksAreRejectedClearly() {
    var mapper = JsonMapper.builder().build();
    for (String ipn :
        List.of(
            "localhost/ipn",
            "https://localhost/ipn",
            "https://127.0.0.1/ipn",
            "http://public.test/ipn",
            "https://user:password@public.test/ipn"))
      assertThatThrownBy(
              () ->
                  new MomoGateway(
                      "MOMO_SANDBOX",
                      "p",
                      "a",
                      "s",
                      "http://localhost:5173/payment/momo-return",
                      ipn,
                      false,
                      mapper))
          .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void verifiesOfficialFieldOrderingAndRejectsChangedAmount() {
    var mapper = JsonMapper.builder().build();
    var gateway =
        new MomoGateway(
            "MOMO_SANDBOX",
            "TEST_PARTNER",
            "test-access",
            "test-secret",
            "https://example.test/result",
            "https://example.test/ipn",
            false,
            mapper);
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("amount", 1000L);
    b.put("extraData", "");
    b.put("message", "Successful.");
    b.put("orderId", "PAY-1");
    b.put("orderInfo", "EduShop PAY-1");
    b.put("orderType", "momo_wallet");
    b.put("partnerCode", "TEST_PARTNER");
    b.put("payType", "qr");
    b.put("requestId", "PAY-1");
    b.put("responseTime", 1721720663942L);
    b.put("resultCode", 0);
    b.put("transId", 4088878653L);
    String canonical =
        "accessKey=test-access&amount=1000&extraData=&message=Successful.&orderId=PAY-1&orderInfo=EduShop"
            + " PAY-1&orderType=momo_wallet&partnerCode=TEST_PARTNER&payType=qr&requestId=PAY-1&responseTime=1721720663942&resultCode=0&transId=4088878653";
    b.put("signature", MomoGateway.hmac("test-secret", canonical));
    gateway.verifyIpn(mapper.valueToTree(b));
    b.put("amount", 2000L);
    assertThatThrownBy(() -> gateway.verifyIpn(mapper.valueToTree(b)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void hmacKnownVector() {
    assertThat(MomoGateway.hmac("key", "The quick brown fox jumps over the lazy dog"))
        .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
  }

  @Test
  void productionAndMissingCredentialsFailFast() {
    var mapper = JsonMapper.builder().build();
    assertThatThrownBy(() -> new MomoGateway("MOMO_SANDBOX", "", "", "", "", "", false, mapper))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new MomoGateway(
                    "MOMO_PRODUCTION", "p", "a", "s", "https://x", "https://x", false, mapper))
        .isInstanceOf(IllegalStateException.class);
  }
}
