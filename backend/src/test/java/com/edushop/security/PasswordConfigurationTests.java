package com.edushop.security;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
class PasswordConfigurationTests {
 @Test void supportsExistingBcryptHashes() {
  var encoder = new PasswordConfiguration().passwordEncoder();
  String password = "Sample-password-2026";
  String hash = new BCryptPasswordEncoder().encode(password);
  assertThat(encoder.matches(password, hash)).isTrue();
  assertThat(encoder.matches(password, "{bcrypt}" + hash)).isTrue();
  assertThat(encoder.matches("wrong-password", hash)).isFalse();
  assertThat(encoder.matches(password, "invalid-hash")).isFalse();
  assertThat(encoder.encode(password)).startsWith("{bcrypt}");
 }
}
