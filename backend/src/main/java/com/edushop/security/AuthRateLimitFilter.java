package com.edushop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance, bounded limiter. Uses socket IP; never trusts spoofable forwarding headers. */
public final class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Bucket(long expires, int count) {}

  private final Map<String, Bucket> buckets = new HashMap<>();
  private final Clock clock;
  private static final Map<String, Integer> LIMITS =
      Map.of(
          "/api/v1/auth/login",
          20,
          "/api/v1/auth/register",
          10,
          "/api/v1/auth/password/request",
          5,
          "/api/v1/auth/password/reset",
          10);

  public AuthRateLimitFilter(Clock clock) {
    this.clock = clock;
  }

  private synchronized long retryAfter(String key, int limit) {
    long now = clock.millis();
    Bucket bucket = buckets.get(key);
    if (bucket == null || bucket.expires <= now) {
      // Bound memory even when requests originate from many different addresses.
      if (buckets.size() >= 10000) buckets.values().removeIf(value -> value.expires <= now);
      if (bucket == null && buckets.size() >= 10000) return 60;
      bucket = new Bucket(now + 60000, 0);
    }
    if (bucket.count >= limit) return Math.max(1, (bucket.expires - now + 999) / 1000);
    buckets.put(key, new Bucket(bucket.expires, bucket.count + 1));
    return 0;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path =
        org.springframework.web.util.UriUtils.decode(
            request.getRequestURI(), java.nio.charset.StandardCharsets.UTF_8);
    Integer limit = LIMITS.get(path);
    if (!"POST".equals(request.getMethod()) || limit == null) {
      chain.doFilter(request, response);
      return;
    }
    long retry = retryAfter(path + ":" + request.getRemoteAddr(), limit);
    if (retry == 0) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(retry));
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Bạn thao tác quá nhiều"
                + " lần. Vui lòng thử lại sau "
                + retry
                + " giây.\"}");
  }
}
