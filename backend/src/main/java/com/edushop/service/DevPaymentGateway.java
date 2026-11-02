package com.edushop.service;
import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component @Profile("dev & !prod & !test")
@ConditionalOnProperty(name="payment.mode",havingValue="DEV_SIMULATOR")
public class DevPaymentGateway implements PaymentGateway{
    public String providerName(){return "DEV_SIMULATOR";}
    public GatewaySession initiate(String ref,BigDecimal amount,String currency,String key){
        return new GatewaySession("DEV-"+ref,ref,null);
    }
}
