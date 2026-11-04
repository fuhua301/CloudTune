package com.cloudtune.player;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Credentials never leave this installation except in HTTPS requests to Kugou. */
public class AccountStore {
    private final Context ctx;
    public AccountStore(Context c) { ctx=c.getApplicationContext(); }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias("cloudtune.session")) {
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("cloudtune.session",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build()); g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("cloudtune.session",null);
    }
    public synchronized void save(JSONObject data) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        String packed=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(data.toString().getBytes("UTF-8")),Base64.NO_WRAP);
        if(!ctx.getSharedPreferences("secure",0).edit().putString("session",packed).commit()) throw new Exception("无法保存登录状态");
    }
    public synchronized JSONObject read() {
        try {
            String[] p=ctx.getSharedPreferences("secure",0).getString("session","").split(":");
            if(p.length!=2) return new JSONObject();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],0)));
            return new JSONObject(new String(c.doFinal(Base64.decode(p[1],0)),"UTF-8"));
        } catch(Exception e){ return new JSONObject(); }
    }
    static boolean compatible(JSONObject j){return j.optInt("auth_version",0)>=KugouApi.AUTH_VERSION&&KugouApi.APP_ID.equals(j.optString("appid"))&&!j.optString("token").isEmpty()&&j.optString("userid").matches("[1-9][0-9]*");}
    public boolean loggedIn(){return compatible(read());}
    public void clear(){ctx.getSharedPreferences("secure",0).edit().clear().commit();}
}
