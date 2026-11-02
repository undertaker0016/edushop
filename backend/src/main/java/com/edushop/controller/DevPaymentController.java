package com.edushop.controller;
import com.edushop.service.PaymentService;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @Profile("dev & !prod & !test")
@ConditionalOnProperty(name="payment.mode",havingValue="DEV_SIMULATOR")
@RequestMapping("/api/v1/admin/dev/payments")
@Tag(name="DEV ONLY - Payment simulator") @SecurityRequirement(name="bearerAuth")
public class DevPaymentController{
    private final PaymentService service;
    public DevPaymentController(PaymentService service){this.service=service;}
    @PostMapping("/{id}/result") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void result(@PathVariable long id,@RequestParam boolean succeeded){
        var p=service.payment(id);
        if(!"DEV_SIMULATOR".equals(p.get("provider")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only DEV_SIMULATOR attempts are accepted");
        service.applyVerifiedResult("DEV_SIMULATOR",(String)p.get("provider_transaction_id"),
            ((Number)p.get("order_id")).longValue(),(java.math.BigDecimal)p.get("amount"),(String)p.get("currency"),succeeded);
    }
}
