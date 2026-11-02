package com.edushop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionGuard {
  public ProductionGuard(
      Environment env,
      @Value("${payment.mode:UNAVAILABLE}") String mode,
      @Value("${security.jwt.secret-base64}") String secret) {
    if (env.matchesProfiles("dev", "test")
        || mode.equals("DEV_SIMULATOR")
        || mode.endsWith("_SANDBOX"))
      throw new IllegalStateException(
          "Production cannot enable dev/test profiles or simulated/sandbox payments");
    if (secret.equals("VGoInlQmRhDir0Tp38/uwrIvvRFgm8b1wPse+vhAJfg="))
      throw new IllegalStateException("The development JWT secret is forbidden in production");
  }
}
