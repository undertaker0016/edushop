package com.edushop.service;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

/** MoMo captureWallet adapter. See official wallet/onetime and payment-api/query documentation. */
@Component @Profile("!test")
@ConditionalOnExpression("'${payment.mode:UNAVAILABLE}' == 'MOMO_SANDBOX' or '${payment.mode:UNAVAILABLE}' == 'MOMO_PRODUCTION'")
public class MomoGateway implements PaymentGateway {
    private final String mode, partner, access, secret, redirect, ipn, base;
    private final ObjectMapper mapper;
    private final HttpClient http;
    public MomoGateway(@Value("${payment.mode}") String mode,
        @Value("${payment.momo.partner-code:}") String partner,
        @Value("${payment.momo.access-key:}") String access,
        @Value("${payment.momo.secret-key:}") String secret,
        @Value("${payment.momo.redirect-url:}") String redirect,
        @Value("${payment.momo.ipn-url:}") String ipn,
        @Value("${payment.momo.production-enabled:false}") boolean productionEnabled,
        ObjectMapper mapper){
        this.mode=mode;this.partner=partner;this.access=access;this.secret=secret;this.redirect=redirect;this.ipn=ipn;this.mapper=mapper;
        if(partner.isBlank()||access.isBlank()||secret.isBlank()||redirect.isBlank()||ipn.isBlank())
            throw new IllegalStateException("MoMo requires partner code, access key, secret key, redirect URL and IPN URL");
        if(mode.equals("MOMO_PRODUCTION")&&!productionEnabled)throw new IllegalStateException("Production payment requires explicit enablement after provider verification");
        if(!URI.create(ipn).getScheme().equals("https"))throw new IllegalStateException("MoMo IPN requires a publicly reachable HTTPS URL");
        base=mode.equals("MOMO_SANDBOX")?"https://test-payment.momo.vn":"https://payment.momo.vn";
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    public String providerName(){return mode;}
    public GatewaySession initiate(String ref,BigDecimal amount,String currency,String key){
        long value;
        try{value=amount.longValueExact();}catch(ArithmeticException ex){throw error(400,"MoMo requires integer VND");}
        if(!"VND".equals(currency)||value<1000||value>50000000)throw error(400,"MoMo amount must be 1,000 to 50,000,000 integer VND");
        Map<String,Object> b=new LinkedHashMap<>();
        b.put("partnerCode",partner);b.put("requestId",ref);b.put("orderId",ref);b.put("amount",value);
        b.put("orderInfo","EduShop "+ref);b.put("redirectUrl",redirect);b.put("ipnUrl",ipn);
        b.put("requestType","captureWallet");b.put("extraData","");b.put("autoCapture",true);b.put("lang","vi");
        b.put("signature",signFields(b,List.of("accessKey","amount","extraData","ipnUrl","orderId","orderInfo","partnerCode","redirectUrl","requestId","requestType")));
        JsonNode result=post("/v2/gateway/api/create",b);
        verifyReference(result,ref,ref,amount);
        if(result.path("resultCode").asInt(-1)!=0)throw error(502,"MoMo did not issue a payment URL; reconcile before retrying");
        String url=required(result,"payUrl");
        URI uri=URI.create(url);
        if(!"https".equals(uri.getScheme())||!URI.create(base).getHost().equals(uri.getHost()))
            throw error(502,"Unexpected MoMo payment URL");
        return new GatewaySession(null,ref,url);
    }
    public JsonNode query(String ref){
        String request=UUID.randomUUID().toString();
        Map<String,Object> b=new LinkedHashMap<>();
        b.put("partnerCode",partner);b.put("orderId",ref);b.put("requestId",request);b.put("lang","vi");
        b.put("signature",signFields(b,List.of("accessKey","orderId","partnerCode","requestId")));
        JsonNode result=post("/v2/gateway/api/query",b);
        if(!partner.equals(required(result,"partnerCode"))||!ref.equals(required(result,"orderId"))||!request.equals(required(result,"requestId")))
            throw error(502,"MoMo query reference mismatch");
        return result;
    }
    public void verifyIpn(JsonNode body){
        List<String> fields=List.of("amount","extraData","message","orderId","orderInfo","orderType","partnerCode","payType","requestId","responseTime","resultCode","transId");
        Map<String,Object> data=new LinkedHashMap<>();
        for(String f:fields)data.put(f,required(body,f));
        List<String> signed=new ArrayList<>();signed.add("accessKey");signed.addAll(fields);
        String expected=signFields(data,signed), supplied=required(body,"signature");
        if(!partner.equals(data.get("partnerCode"))||!constantEquals(expected,supplied))throw error(401,"Invalid MoMo signature");
        if(!body.path("amount").isIntegralNumber()||!body.path("transId").isIntegralNumber()||!body.path("resultCode").isIntegralNumber())
            throw error(400,"Malformed MoMo numeric fields");
    }
    private void verifyReference(JsonNode r,String ref,String request,BigDecimal amount){
        if(!partner.equals(required(r,"partnerCode"))||!ref.equals(required(r,"orderId"))||!request.equals(required(r,"requestId"))
            ||new BigDecimal(required(r,"amount")).compareTo(amount)!=0)throw error(502,"MoMo response reference mismatch");
    }
    private String signFields(Map<String,Object> values,List<String> keys){
        StringJoiner text=new StringJoiner("&");
        for(String k:keys)text.add(k+"="+(k.equals("accessKey")?access:Objects.toString(values.get(k),"")));
        return hmac(secret,text.toString());
    }
    public static String hmac(String secret,String text){
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException ex){throw new IllegalStateException(ex);}
    }
    private static boolean constantEquals(String a,String b){
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));
    }
    private JsonNode post(String path,Map<String,Object> body){
        try{
            HttpRequest request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(35))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw error(502,"MoMo returned an HTTP error; reconcile the durable payment attempt");
            return mapper.readTree(response.body());
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw error(503,"MoMo call interrupted");}
        catch(java.io.IOException ex){throw error(503,"MoMo connection failed; retry the same payment key");}
    }
    public static String required(JsonNode body,String field){
        JsonNode n=body.get(field);
        if(n==null||n.isNull()||n.isArray()||n.isObject())throw error(400,"Missing or invalid MoMo field: "+field);
        return n.asText();
    }
    private static ResponseStatusException error(int code,String text){return new ResponseStatusException(HttpStatus.valueOf(code),text);}
}
