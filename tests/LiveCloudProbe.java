package com.cloudtune.player;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import org.json.*;
/** Tests only uid=0 with an empty token. No real account is requested. */
public class LiveCloudProbe {
    public static void main(String[] args)throws Exception{
        PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(KugouApi.RSA_KEY)));
        String mid=KugouApi.calculateMid("cloudtune-anonymous-wire-test");
        for(String mode:KugouApi.CLOUD_FORMATS){
            KugouApi.CloudFrame frame=KugouApi.cloudFrame(new JSONObject().put("userid","123456789").put("token","local-test-only"),1,mode,mid,String.valueOf(System.currentTimeMillis()/1000),"eeeeee",pub);
            // Replace the local dummy identity with uid=0 before any network request.
            String auth="{\"aes\":\"eeeeee\",\"uid\":"+(KugouApi.numericUid(mode)?"0":"\"0\"")+",\"token\":\"\"}";
            Cipher c=Cipher.getInstance("RSA/ECB/PKCS1Padding");c.init(Cipher.ENCRYPT_MODE,pub);frame.params.put("p",KugouApi.hex(c.doFinal(auth.getBytes("UTF-8"))).toUpperCase(Locale.US));
            byte[] reply=WireClient.request("https://mcloudservice.kugou.com","/v1/get_list",frame.params,frame.body,null,false,mid,frame.headers);
            KugouApi.CloudReply r=KugouApi.decodeCloud(reply,"eeeeee");
            if(!KugouApi.cloudOk(r.body))throw new AssertionError(mode+" anonymous request rejected: "+KugouApi.cloudCode(r.body));
            System.out.println("PASS: "+mode+" actual cloud HTTP request and encrypted response; uid=0 only");
            if(mode.equals("S-F")){
                frame.params.put("clienttime",String.valueOf(System.currentTimeMillis()/1000));frame.params.put("key",KugouApi.md5(KugouApi.APP_ID+KugouApi.ANDROID_SALT+KugouApi.CLIENT_VERSION+frame.params.get("clienttime")));
                reply=WireClient.request("https://mcloudservice.kugou.com","/v1/get_list",frame.params,new byte[]{1,2,3},null,false,mid,frame.headers);
                r=KugouApi.decodeCloud(reply,"eeeeee");
                if(!KugouApi.cloudCode(r.body).equals("20010"))throw new AssertionError("Invalid-body control not rejected");
                System.out.println("PASS: deliberately invalid body is rejected with 20010 (control)");
            }
        }
        System.out.println("No real account login or personal cloud was tested.");
    }
}
