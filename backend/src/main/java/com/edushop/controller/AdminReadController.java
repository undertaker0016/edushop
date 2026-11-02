package com.edushop.controller;
import com.edushop.service.*;
import com.edushop.dto.admin.AdminPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name="Admin") @SecurityRequirement(name="bearerAuth")
public class AdminReadController {
    private final AdminReadService read;
    private final CheckoutService orders;
    private final PaymentService payments;
    public AdminReadController(AdminReadService read,CheckoutService orders,PaymentService payments){this.read=read;this.orders=orders;this.payments=payments;}
    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(@RequestParam(required=false) LocalDateTime from,@RequestParam(required=false) LocalDateTime to){return read.dashboard(from,to);}
    @GetMapping({"/browse/{module:users|instructors|courses|offerings|sessions|orders|payments|enrollments|reviews|categories}",
        "/{module:instructors|courses|offerings|sessions|orders|payments|reviews}"})
    public AdminPage browse(@PathVariable String module,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) Long userId,
        @RequestParam(required=false) Long offeringId,@RequestParam(required=false) Long courseId,@RequestParam(required=false) String provider,
        @RequestParam(required=false) LocalDateTime from,@RequestParam(required=false) LocalDateTime to){
        return read.list(module,page,size,q,status,userId,offeringId,courseId,provider,from,to);
    }
    @GetMapping("/{module:users|payments|enrollments}/{id}")
    public Map<String,Object> detail(@PathVariable String module,@PathVariable long id){return read.detail(module,id);}
    @GetMapping("/orders/{id}")
    public Map<String,Object> order(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){
        return Map.of("order",orders.get(Long.parseLong(jwt.getSubject()),true,id),"payments",payments.listForOrder(Long.parseLong(jwt.getSubject()),true,id));
    }
    @DeleteMapping("/orders/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){orders.cancel(Long.parseLong(jwt.getSubject()),true,id);}
    @PatchMapping("/reviews/{id}/hidden") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void moderate(@PathVariable long id,@RequestParam boolean hidden){read.moderate(id,hidden);}
}

