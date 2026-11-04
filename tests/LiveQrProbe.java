package com.cloudtune.player;
import java.util.*;
import org.json.*;
public class LiveQrProbe {
    public static void main(String[] a)throws Exception{
        String mid=KugouApi.calculateMid("cloudtune-device-protocol-public-test");String dev="TEST123456";
        TreeMap<String,String> p=KugouApi.qrParams(mid,String.valueOf(System.currentTimeMillis()/1000));
        JSONObject r=new JSONObject(new String(WireClient.request("https://login-user.kugou.com","/v2/qrcode",p,null,KugouApi.WEB_SALT,true,mid,null),"UTF-8"));
        if(r.optInt("status")!=1)throw new AssertionError("QR generation status "+r.optInt("status")+" code "+r.optInt("error_code"));
        String key=r.getJSONObject("data").getString("qrcode");
        p=KugouApi.qrCheckParams(mid,String.valueOf(System.currentTimeMillis()/1000),key,dev);
        r=new JSONObject(new String(WireClient.request("https://login-user.kugou.com","/v2/get_userinfo_qrcode",p,null,KugouApi.WEB_SALT,true,mid,null),"UTF-8"));
        if(r.optInt("status")!=1||r.getJSONObject("data").optInt("status")!=1)throw new AssertionError("QR polling failed");
        System.out.println("PASS: live Java transport, decimal MID, fixed DEV, Android QR generation and waiting-scan state; no account authorized");
    }
}
