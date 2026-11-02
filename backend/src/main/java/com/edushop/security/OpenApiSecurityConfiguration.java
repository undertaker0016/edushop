package com.edushop.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class OpenApiSecurityConfiguration {

  @Bean
  SecurityFilterChain developmentSecurityFilterChain(
      HttpSecurity http,
      DatabaseJwtAuthenticationConverter jwtAuthenticationConverter,
      CorsConfigurationSource corsConfigurationSource,
      ObjectMapper objectMapper,
      @org.springframework.beans.factory.annotation.Value(
              "${edushop.security.auth-rate-limit:true}")
          boolean rateLimit)
      throws Exception {
    if (rateLimit)
      http.addFilterBefore(
          new AuthRateLimitFilter(java.time.Clock.systemUTC()),
          org.springframework.security.oauth2.server.resource.web.authentication
              .BearerTokenAuthenticationFilter.class);
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/password/request",
                        "/api/v1/auth/password/reset")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/reviews/courses/**")
                    .permitAll()
						.requestMatchers("/api/v1/reviews/mine/**").authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/courses/**",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/media/**",
                        "/api/v1/offerings/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/instructor/**")
                    .hasAnyRole("ADMIN", "INSTRUCTOR")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/courses/**",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/offerings/**")
                    .hasAnyRole("ADMIN", "INSTRUCTOR")
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/courses/**",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/offerings/**")
                    .hasAnyRole("ADMIN", "INSTRUCTOR")
                    .requestMatchers(
                        HttpMethod.DELETE,
                        "/api/v1/courses/**",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/offerings/**")
                    .hasAnyRole("ADMIN", "INSTRUCTOR")
                    .requestMatchers(
                        "/api/v1/auth/me",
                        "/api/v1/auth/me/**",
                        "/api/v1/users/me/**",
                        "/api/v1/orders/**",
                        "/api/v1/payments/**",
                        "/api/v1/enrollments/**",
                        "/api/v1/reviews/**",
                        "/api/v1/wishlists/**",
                        "/api/v1/notifications/**",
                        "/api/v1/media",
                        "/api/v1/instructor-applications",
                        "/api/v1/coupons/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeSecurityProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeSecurityProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Access is denied")))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeSecurityProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Invalid or expired bearer token"))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
    return http.build();
  }

  private static void writeSecurityProblem(
      ObjectMapper mapper,
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      HttpStatus status,
      String message)
      throws java.io.IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setTitle(status.getReasonPhrase());
    problem.setType(java.net.URI.create("https://api.edushop.local/problems/" + status.value()));
    problem.setInstance(java.net.URI.create(request.getRequestURI()));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
