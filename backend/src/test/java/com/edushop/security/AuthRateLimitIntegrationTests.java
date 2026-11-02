package com.edushop.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "edushop.security.auth-rate-limit=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthRateLimitIntegrationTests {
  @Autowired MockMvc mvc;

  @Test
  void filterProtectsPublicLoginAndPreservesCorsAndCatalog() throws Exception {
    for (int i = 0; i < 20; i++)
      mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", "http://localhost:5173")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(jsonPath("$.status").value(429));
    mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
  }
}
