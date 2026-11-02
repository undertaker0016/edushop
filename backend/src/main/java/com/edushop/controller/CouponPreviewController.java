package com.edushop.controller;

import com.edushop.service.CouponPreviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponPreviewController {
  private final CouponPreviewService service;

  public CouponPreviewController(CouponPreviewService service) {
    this.service = service;
  }

  public record Preview(
      @NotEmpty @Size(max = 20) List<@NotNull @Positive Long> offeringIds,
      @NotBlank @Size(max = 50) String code) {}

  @GetMapping("/available")
  public Object available(@AuthenticationPrincipal Jwt jwt, @RequestParam List<Long> offeringIds) {
    return service.available(Long.parseLong(jwt.getSubject()), offeringIds);
  }

  @PostMapping("/preview")
  public Object preview(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Preview r) {
    return service.preview(Long.parseLong(jwt.getSubject()), r.offeringIds(), r.code());
  }

  @GetMapping("/quote")
  public Object quote(@RequestParam List<Long> offeringIds) {
    return java.util.Map.of("subtotal", service.subtotal(offeringIds));
  }
}
