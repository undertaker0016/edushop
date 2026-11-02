package com.edushop.controller;

import com.edushop.dto.notifications.CampaignRequest;
import com.edushop.service.NotificationCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @Validated
@RequestMapping({"/api/v1/admin/notification-campaigns", "/api/v1/instructor/notification-campaigns"})
@Tag(name="Notification campaigns") @SecurityRequirement(name="bearerAuth")
public class NotificationCampaignController {
    private final NotificationCampaignService service;
    public NotificationCampaignController(NotificationCampaignService service) { this.service=service; }
    @PostMapping("/preview") @Operation(summary="Preview audience count and up to five names within sender permissions")
    public Map<String,Object> preview(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CampaignRequest body) { return service.preview(Long.parseLong(jwt.getSubject()),body); }
    @PostMapping @Operation(summary="Atomically snapshot recipients and send an idempotent in-app campaign")
    public Map<String,Object> send(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CampaignRequest body) { return service.send(Long.parseLong(jwt.getSubject()),key,body); }
    @GetMapping @Operation(summary="List campaign history; instructors see only their own sends")
    public Map<String,Object> history(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") @Size(max=40) String audience,@RequestParam(defaultValue="") @Size(max=40) String type) { return service.history(Long.parseLong(jwt.getSubject()),page,audience,type); }
    @GetMapping("/options/{kind}") @Operation(summary="Search courses or assigned offerings for audience selection")
    public List<Map<String,Object>> options(@AuthenticationPrincipal Jwt jwt,@PathVariable String kind,@RequestParam(defaultValue="") @Size(max=200) String q) { return service.options(Long.parseLong(jwt.getSubject()),kind,q); }
}
