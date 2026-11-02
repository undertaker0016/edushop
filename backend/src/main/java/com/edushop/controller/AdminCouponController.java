package com.edushop.controller;

import com.edushop.dto.admin.CouponCreateRequest;
import com.edushop.dto.admin.CouponResponse;
import com.edushop.service.AdminCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminCouponController {
	@GetMapping("/{code}")
	public CouponResponse detail(@PathVariable String code){return service.find(code);}
	@PutMapping("/{code}")
	public CouponResponse update(@PathVariable String code,@Valid @RequestBody CouponCreateRequest r){return service.update(code,r);}
	@GetMapping("/{code}/redemptions")
	public java.util.List<java.util.Map<String,Object>> history(@PathVariable String code){return service.history(code);}
	private final AdminCouponService service;
	public AdminCouponController(AdminCouponService service) { this.service = service; }

	@PostMapping @ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a percent or fixed VND coupon")
	public CouponResponse create(@Valid @RequestBody CouponCreateRequest request) { return service.create(request); }

	@GetMapping @Operation(summary = "List coupons and usage counts")
	public List<CouponResponse> list() { return service.list(); }

	@PatchMapping("/{code}/active") @Operation(summary = "Activate or deactivate a coupon")
	public CouponResponse active(@PathVariable String code, @RequestParam boolean active) { return service.setActive(code, active); }
}
