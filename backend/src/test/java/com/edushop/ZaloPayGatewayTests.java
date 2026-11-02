package com.edushop;

import static org.assertj.core.api.Assertions.*;

import com.edushop.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

class ZaloPayGatewayTests {
  final JsonMapper mapper = JsonMapper.builder().build();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC);

  ZaloPayGateway gateway(boolean shared) {
    return new ZaloPayGateway(
        123,
        "key1",
        "key2",
        "http://localhost:5173/payment/zalopay-return",
        "",
        shared,
        mapper,
        clock);
  }

  @Test
  void referenceUsesVietnamDateIsStableAndFitsProviderLimit() {
    var g = gateway(false);
    String ref = g.reference("same-key", clock.instant());
    assertThat(ref).matches("261031_[0-9a-f]{32}").hasSize(39);
    assertThat(g.reference("same-key", clock.instant())).isEqualTo(ref);
    assertThat(g.reference("another-key", clock.instant())).isNotEqualTo(ref);
  }

  @Test
  void createAndQueryUseDifferentOfficialMacFormats() {
    var g =
        new ZaloPayGateway(
            123,
            "key1",
            "key2",
            "http://localhost:5173/payment/zalopay-return",
            "",
            true,
            mapper,
            clock) {
          @Override
          protected JsonNode post(String path, Map<String, Object> body) {
            String ref = (String) body.get("app_trans_id");
            if (path.equals("/v2/create")) {
              assertThat(body).doesNotContainKeys("email", "phone", "callback_url");
              assertThat(body.get("mac"))
                  .isEqualTo(
                      MomoGateway.hmac(
                          "key1",
                          "123|"
                              + ref
                              + "|EduShop|100000|"
                              + clock.millis()
                              + "|"
                              + body.get("embed_data")
                              + "|[]"));
              return mapper.valueToTree(
                  Map.of(
                      "return_code",
                      1,
                      "order_url",
                      "https://qcgateway.zalopay.vn/openinapp?order=demo"));
            }
            assertThat(path).isEqualTo("/v2/query");
            assertThat(body.get("mac")).isEqualTo(MomoGateway.hmac("key1", "123|" + ref + "|key1"));
            return mapper.valueToTree(Map.of("return_code", 3));
          }
        };
    String ref = g.reference("key", clock.instant());
    assertThat(g.initiate(ref, new BigDecimal("100000"), "VND", "key").checkoutUrl())
        .startsWith("https://qcgateway.zalopay.vn");
    assertThat(g.query(ref).path("return_code").asInt()).isEqualTo(3);
  }

  @Test
  void callbackUsesExactRawDataAndRejectsTamperingWrongAppAndPublicKeys() {
    var g = gateway(false);
    String ref = g.reference("test", clock.instant());
    String raw =
        mapper.writeValueAsString(
            Map.of("app_id", 123, "app_trans_id", ref, "amount", 100000, "zp_trans_id", 12345));
    JsonNode body =
        mapper.valueToTree(Map.of("type", 1, "data", raw, "mac", MomoGateway.hmac("key2", raw)));
    assertThat(g.verifiedCallback(body).path("amount").asLong()).isEqualTo(100000);
    JsonNode changed =
        mapper.valueToTree(
            Map.of(
                "type",
                1,
                "data",
                raw.replace("100000", "200000"),
                "mac",
                MomoGateway.hmac("key2", raw)));
    assertThatThrownBy(() -> g.verifiedCallback(changed))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> gateway(true).verifiedCallback(body))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    JsonNode empty=mapper.valueToTree(Map.of("type",1,"data","","mac",MomoGateway.hmac("key2","")));
    assertThatThrownBy(()->g.verifiedCallback(empty)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void rejectsNonSandboxRedirectsAndFractionalAmounts() {
    for (String url :
        List.of(
            "https://gateway.zalopay.vn/pay",
            "http://qcgateway.zalopay.vn/pay",
            "https://qcgateway.zalopay.vn.evil.test/pay",
            "https://user:pass@qcgateway.zalopay.vn/pay"))
      assertThatThrownBy(() -> ZaloPayGateway.validateCheckout(url))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    var g = gateway(false);
    assertThatThrownBy(
            () ->
                g.initiate(
                    g.reference("key", clock.instant()), new BigDecimal("1000.5"), "VND", "key"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
