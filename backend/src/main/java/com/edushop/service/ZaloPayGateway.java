package com.edushop.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!test & !prod")
@ConditionalOnProperty(name = "payment.mode", havingValue = "ZALOPAY_SANDBOX")
public class ZaloPayGateway implements PaymentGateway {
  private final int appId;
  private final String key1, key2, redirect, callback;
  private final boolean publicDemo;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  public ZaloPayGateway(
      @Value("${payment.zalopay.app-id:0}") int appId,
      @Value("${payment.zalopay.key1:}") String key1,
      @Value("${payment.zalopay.key2:}") String key2,
      @Value("${payment.zalopay.redirect-url}") String redirect,
      @Value("${payment.zalopay.callback-url:}") String callback,
      @Value("${payment.zalopay.public-demo:false}") boolean publicDemo,
      ObjectMapper mapper,
      Clock clock) {
    if (appId <= 0 || key1.isBlank() || key2.isBlank())
      throw new IllegalStateException("ZaloPay sandbox cần App ID, Key1, Key2.");
    validateUrl(redirect, false);
    if (!callback.isBlank()) validateUrl(callback, true);
    this.appId = appId;
    this.key1 = key1;
    this.key2 = key2;
    this.redirect = redirect;
    this.callback = callback;
    this.publicDemo = publicDemo;
    this.mapper = mapper;
    this.clock = clock;
  }

  public String providerName() {
    return "ZALOPAY_SANDBOX";
  }

  public boolean publicDemo() {
    return publicDemo;
  }

  @Override
  public String reference(String key, Instant now) {
    return DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(now)
        + "_"
        + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
  }

  public GatewaySession initiate(String ref, BigDecimal amount, String currency, String key) {
    validateReference(ref);
    long value;
    try {
      value = amount.longValueExact();
    } catch (ArithmeticException ex) {
      throw error(400, "ZaloPay chỉ nhận số tiền nguyên VND.");
    }
    if (!"VND".equals(currency) || value < 1000 || value > 50000000)
      throw error(400, "Số tiền thử nghiệm phải từ 1.000 đến 50.000.000 VND.");
    Map<String, Object> body = new LinkedHashMap<>();
    String embed = mapper.writeValueAsString(Map.of("redirecturl", redirect + "?reference=" + ref));
    body.put("app_id", appId);
    body.put("app_user", "EduShop");
    body.put("app_trans_id", ref);
    body.put("app_time", clock.millis());
    body.put("amount", value);
    body.put("item", "[]");
    body.put("embed_data", embed);
    body.put("bank_code", "");
    body.put("description", "EduShop sandbox - " + ref);
    body.put("expire_duration_seconds", 900);
    if (!publicDemo && !callback.isBlank()) body.put("callback_url", callback);
    // Use the same timestamp included in the request, even across millisecond boundaries.
    String canonical =
        appId + "|" + ref + "|EduShop|" + value + "|" + body.get("app_time") + "|" + embed + "|[]";
    body.put("mac", MomoGateway.hmac(key1, canonical));
    JsonNode result = post("/v2/create", body);
    if (result.path("return_code").asInt(-1) != 1)
      throw error(
          502,
          "ZaloPay chưa tạo được giao dịch (mã "
              + result.path("sub_return_code").asInt(-1)
              + "). Kiểm tra trạng thái trước khi thử lại.");
    String url = result.path("order_url").asText("");
    validateCheckout(url);
    return new GatewaySession(null, ref, url);
  }

  public JsonNode query(String ref) {
    validateReference(ref);
    return post(
        "/v2/query",
        Map.of(
            "app_id",
            appId,
            "app_trans_id",
            ref,
            "mac",
            MomoGateway.hmac(key1, appId + "|" + ref + "|" + key1)));
  }

  public JsonNode verifiedCallback(JsonNode body) {
    if (publicDemo)
      throw error(
          403, "Khóa thử công khai chỉ xác nhận bằng truy vấn ZaloPay, không dùng callback.");
    if (!body.path("data").isTextual()
        || !body.path("mac").isTextual()
        || body.path("type").asInt(-1) != 1) throw error(400, "Callback ZaloPay không hợp lệ.");
    String raw = body.path("data").asText();
    String expected = MomoGateway.hmac(key2, raw);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        body.path("mac").asText().getBytes(StandardCharsets.US_ASCII)))
      throw error(401, "Chữ ký ZaloPay không hợp lệ.");
    JsonNode data;
    try {
      data = mapper.readTree(raw);
    } catch (Exception ex) {
      throw error(400, "Dữ liệu callback ZaloPay không hợp lệ.");
    }
    if (data == null || !data.isObject()
        || !data.path("app_id").isIntegralNumber()
        || data.path("app_id").asInt() != appId
        || !data.path("amount").isIntegralNumber()
        || data.path("amount").asLong() <= 0
        || !data.path("zp_trans_id").isIntegralNumber()
        || data.path("zp_trans_id").asLong() <= 0)
      throw error(400, "Sai ứng dụng hoặc số tiền/mã giao dịch ZaloPay.");
    validateReference(data.path("app_trans_id").asText(""));
    return data;
  }

  protected JsonNode post(String path, Map<String, Object> body) {
    try {
      var request =
          HttpRequest.newBuilder(URI.create("https://sb-openapi.zalopay.vn" + path))
              .timeout(Duration.ofSeconds(25))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw error(502, "ZaloPay chưa phản hồi thành công. Kiểm tra lại trạng thái giao dịch.");
      var result = mapper.readTree(response.body());
      if (result == null || !result.isObject() || !result.path("return_code").isIntegralNumber())
        throw error(502, "Phản hồi ZaloPay không hợp lệ.");
      return result;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw error(503, "Kết nối ZaloPay bị gián đoạn.");
    } catch (java.io.IOException ex) {
      throw error(503, "Không kết nối được ZaloPay sandbox. Thử kiểm tra lại trạng thái.");
    }
  }

  public static void validateCheckout(String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equals(uri.getScheme())
          || !Set.of("qcgateway.zalopay.vn", "sbgateway.zalopay.vn").contains(uri.getHost())
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw error(502, "Đường dẫn thanh toán không thuộc ZaloPay sandbox.");
    }
  }

  private static void validateReference(String value) {
    if (value == null || !value.matches("[0-9]{6}_[0-9a-f]{32}"))
      throw error(400, "Mã giao dịch ZaloPay không hợp lệ.");
  }

  private static void validateUrl(String value, boolean callback) {
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || uri.getQuery() != null
          || !("https".equals(uri.getScheme()) || (!callback && "http".equals(uri.getScheme()))))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("URL quay về/callback ZaloPay không hợp lệ.");
    }
  }

  private static ResponseStatusException error(int status, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(status), message);
  }
}
