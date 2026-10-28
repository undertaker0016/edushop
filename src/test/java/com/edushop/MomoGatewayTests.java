package com.edushop;
import org.junit.jupiter.api.Test;
import com.edushop.service.MomoGateway;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class MomoGatewayTests{
    @Test void verifiesOfficialFieldOrderingAndRejectsChangedAmount(){
        var mapper=JsonMapper.builder().build();
        var gateway=new MomoGateway("MOMO_SANDBOX","TEST_PARTNER","test-access","test-secret","https://example.test/result","https://example.test/ipn",false,mapper);
        Map<String,Object> b=new LinkedHashMap<>();
        b.put("amount",1000L);b.put("extraData","");b.put("message","Successful.");b.put("orderId","PAY-1");b.put("orderInfo","EduShop PAY-1");
        b.put("orderType","momo_wallet");b.put("partnerCode","TEST_PARTNER");b.put("payType","qr");b.put("requestId","PAY-1");
        b.put("responseTime",1721720663942L);b.put("resultCode",0);b.put("transId",4088878653L);
        String canonical="accessKey=test-access&amount=1000&extraData=&message=Successful.&orderId=PAY-1&orderInfo=EduShop PAY-1&orderType=momo_wallet&partnerCode=TEST_PARTNER&payType=qr&requestId=PAY-1&responseTime=1721720663942&resultCode=0&transId=4088878653";
        b.put("signature",MomoGateway.hmac("test-secret",canonical));
        gateway.verifyIpn(mapper.valueToTree(b));
        b.put("amount",2000L);
        assertThatThrownBy(()->gateway.verifyIpn(mapper.valueToTree(b))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void hmacKnownVector(){
        assertThat(MomoGateway.hmac("key","The quick brown fox jumps over the lazy dog"))
            .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }
    @Test void productionAndMissingCredentialsFailFast(){
        var mapper=JsonMapper.builder().build();
        assertThatThrownBy(()->new MomoGateway("MOMO_SANDBOX","","","","","",false,mapper)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new MomoGateway("MOMO_PRODUCTION","p","a","s","https://x","https://x",false,mapper)).isInstanceOf(IllegalStateException.class);
    }
}
