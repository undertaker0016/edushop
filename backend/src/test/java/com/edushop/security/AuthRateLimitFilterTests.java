package com.edushop.security;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class AuthRateLimitFilterTests {
  static class MutableClock extends Clock {
    Instant time = Instant.parse("2026-10-30T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return time;
    }
  }

  MockHttpServletResponse request(AuthRateLimitFilter filter, String path, String ip, String spoof)
      throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(ip);
    request.addHeader("X-Forwarded-For", spoof);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  void blocksFloodAndIgnoresSpoofedForwardedAddresses() throws Exception {
    var filter = new AuthRateLimitFilter(new MutableClock());
    for (int i = 0; i < 20; i++)
      assertThat(request(filter, "/api/v1/auth/login", "127.0.0.1", "fake" + i).getStatus())
          .isEqualTo(200);
    var blocked = request(filter, "/api/v1/auth/login", "127.0.0.1", "another");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    assertThat(request(filter, "/api/v1/auth/%6cogin", "127.0.0.1", "spoof").getStatus())
        .isEqualTo(429);
    assertThat(request(filter, "/api/v1/auth/login", "127.0.0.2", "another").getStatus())
        .isEqualTo(200);
  }

  @Test
  void expiresWindowAndSeparatesSensitiveActions() throws Exception {
    var clock = new MutableClock();
    var filter = new AuthRateLimitFilter(clock);
    for (int i = 0; i < 5; i++) request(filter, "/api/v1/auth/password/request", "127.0.0.1", "x");
    assertThat(request(filter, "/api/v1/auth/password/request", "127.0.0.1", "x").getStatus())
        .isEqualTo(429);
    assertThat(request(filter, "/api/v1/auth/password/reset", "127.0.0.1", "x").getStatus())
        .isEqualTo(200);
    clock.time = clock.time.plusSeconds(60);
    assertThat(request(filter, "/api/v1/auth/password/request", "127.0.0.1", "x").getStatus())
        .isEqualTo(200);
  }

  @Test
  void doesNotBlockCatalogOrPreflight() throws Exception {
    var filter = new AuthRateLimitFilter(new MutableClock());
    for (int i = 0; i < 30; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"),
          response,
          new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(request(filter, "/api/v1/courses", "127.0.0.1", "x").getStatus()).isEqualTo(200);
    }
  }
}
