package com.cloudtune.player;

import android.content.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Direct HTTPS protocol adapted from MakcRe/KuGouMusicApi (MIT), see third-party notices. */
public class KugouApi {
    static final String APP_ID="1005",CLIENT_VERSION="20489";
    static final int CLOUD_PAGE_SIZE=30,AUTH_VERSION=3;
    static final String ANDROID_SALT="OIlwieks28dk2k092lksi2UIkp";
    static final String WEB_SALT="NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    static final String RSA_KEY="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB";
    final Context ctx; final AccountStore store; final String mid,dev;
    public KugouApi(Context c){ctx=c.getApplicationContext();store=new AccountStore(ctx);
        SharedPreferences p=ctx.getSharedPreferences("device",0);
        String guid=p.getString("guid","");String device=p.getString("dev","");
        try{
            if(guid.isEmpty()){
                String legacy=p.getString("mid","");guid=legacy.matches("[0-9a-fA-F]{32}")?legacy.toLowerCase(Locale.US):md5(UUID.randomUUID().toString());
            }
            if(!device.matches("[A-Z0-9]{10}"))device=UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.US);
            mid=calculateMid(guid);dev=device;
            if(!p.edit().putString("guid",guid).putString("mid",mid).putString("dev",dev).putInt("device_version",2).commit())throw new IllegalStateException("无法保存设备标识");
        }catch(Exception ex){throw new IllegalStateException("无法初始化登录设备");}
    }
    static String calculateMid(String guid) throws Exception{return new BigInteger(md5(guid),16).toString(10);}
    static String md5(String s) throws Exception {return hex(MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")));}
    static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    static TreeMap<String,String> baseParams(String mid,String now,JSONObject account){
        TreeMap<String,String> p=new TreeMap<String,String>();
        p.put("dfid","-");p.put("mid",mid);p.put("uuid","-");p.put("appid",APP_ID);p.put("clientver",CLIENT_VERSION);p.put("clienttime",now);
        if(account!=null){if(!account.optString("token").isEmpty())p.put("token",account.optString("token"));if(!account.optString("userid").isEmpty())p.put("userid",account.optString("userid"));}return p;
    }
    TreeMap<String,String> defaults(){return baseParams(mid,String.valueOf(System.currentTimeMillis()/1000),store.read());}
    static TreeMap<String,String> qrParams(String mid,String now){
        TreeMap<String,String> p=baseParams(mid,now,null);
        p.put("type","1");p.put("plat","4");p.put("srcappid","2919");
        p.put("qrcode_txt","https://h5.kugou.com/apps/loginQRCode/html/index.html?appid="+APP_ID+"&");return p;
    }
    static TreeMap<String,String> qrCheckParams(String mid,String now,String key,String dev){
        TreeMap<String,String> p=baseParams(mid,now,null);p.put("plat","4");p.put("srcappid","2919");p.put("qrcode",key);p.put("dev",dev);return p;
    }
    static String qrUrl(String key) throws Exception{return "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid="+APP_ID+"&qrcode="+URLEncoder.encode(key,"UTF-8");}
    static JSONObject cloudPayload(int page) throws Exception{
        if(page<1)throw new IOException("云盘页码无效");JSONObject j=new JSONObject();j.put("page",page);j.put("pagesize",CLOUD_PAGE_SIZE);j.put("getkmr",1);return j;
    }
    static JSONObject cloudIdentity(JSONObject a,String key) throws Exception{
        String uid=a.optString("userid","");String token=a.optString("token","");
        if(!uid.matches("[1-9][0-9]*")||token.isEmpty())throw new IOException("登录信息不完整，请重新扫码");
        JSONObject j=new JSONObject();j.put("aes",key);j.put("uid",uid);j.put("token",token);return j;
    }
    static JSONObject sessionFromQr(JSONObject d) throws Exception{
        String uid=d.optString("userid","");String token=d.optString("token","");
        if(d.optInt("status")!=4||!uid.matches("[1-9][0-9]*")||token.isEmpty())throw new IOException("授权未返回有效登录信息，请刷新二维码");
        JSONObject a=new JSONObject();a.put("token",token);a.put("userid",uid);a.put("nickname",d.optString("nickname","云盘听友"));a.put("appid",APP_ID);a.put("auth_version",AUTH_VERSION);return a;
    }
    byte[] request(String base,String path,TreeMap<String,String> params,byte[] body,String salt) throws Exception {
        return WireClient.request(base,path,params,body,salt,WEB_SALT.equals(salt),mid,null);
    }
    static PublicKey publicKey() throws Exception{return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(RSA_KEY,Base64.DEFAULT)));}
    static byte[] rawRsa(byte[] data,PublicKey key) throws Exception{
        int size=(((java.security.interfaces.RSAPublicKey)key).getModulus().bitLength()+7)/8;
        if(data.length>size)throw new IOException("认证参数过长");
        byte[] padded=new byte[size];System.arraycopy(data,0,padded,0,data.length);
        Cipher rsa=Cipher.getInstance("RSA/ECB/NoPadding");rsa.init(Cipher.ENCRYPT_MODE,key);return rsa.doFinal(padded);
    }
    String sessionDiagnostic(){
        try{
            JSONObject a=store.read();String now=String.valueOf(System.currentTimeMillis()/1000);
            JSONObject encrypted=new JSONObject();encrypted.put("token",a.optString("token"));encrypted.put("clienttime",Long.parseLong(now));
            JSONObject payload=new JSONObject();payload.put("visit_time",Long.parseLong(now));payload.put("usertype",1);payload.put("p",hex(rawRsa(encrypted.toString().getBytes("UTF-8"),publicKey())).toUpperCase(Locale.US));payload.put("userid",Long.parseLong(a.optString("userid")));
            TreeMap<String,String> params=defaults();params.put("plat","1");Map<String,String> headers=new HashMap<String,String>();headers.put("x-router","usercenter.kugou.com");headers.put("Content-Type","application/json");
            JSONObject r=new JSONObject(new String(WireClient.request("https://gateway.kugou.com","/v3/get_my_info",params,payload.toString().getBytes("UTF-8"),ANDROID_SALT,false,mid,headers),"UTF-8"));
            return diagnosticSummary(r);
        }catch(Exception e){return "账号校验：未取得结果（网络或协议异常）";}
    }
    static String diagnosticSummary(JSONObject r){
        if(r.optInt("status",0)==1)return "账号校验：通过；云盘列表接口仍拒绝请求";
        String code=Track.first(r,"error_code","err_code","errcode","code");
        return "账号校验：未通过"+(code.matches("[0-9]{1,10}")?"（代码 "+code+"）":"");
    }
    static class ApiException extends IOException {
        final String code;
        ApiException(String message,String c){super(message);code=c;}
    }
    JSONObject json(String base,String path,TreeMap<String,String> p,String salt) throws Exception{return new JSONObject(new String(request(base,path,p,null,salt),"UTF-8"));}
    public String qrKey() throws Exception{
        TreeMap<String,String> p=qrParams(mid,String.valueOf(System.currentTimeMillis()/1000));
        JSONObject r=json("https://login-user.kugou.com","/v2/qrcode",p,WEB_SALT);
        JSONObject d=r.optJSONObject("data");if(d==null||d.optString("qrcode").isEmpty())throw apiError(r,"无法取得登录二维码");return d.getString("qrcode");
    }
    public JSONObject qrCheck(String key) throws Exception{
        TreeMap<String,String> p=qrCheckParams(mid,String.valueOf(System.currentTimeMillis()/1000),key,dev);
        JSONObject r=json("https://login-user.kugou.com","/v2/get_userinfo_qrcode",p,WEB_SALT);
        JSONObject d=r.optJSONObject("data");if(d==null)throw apiError(r,"无法检查登录状态");return d;
    }
    public void acceptLogin(JSONObject d) throws Exception{store.save(sessionFromQr(d));}
    static byte[] aes(int mode,byte[] input,String key) throws Exception{
        String h=md5(key);Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(mode,new SecretKeySpec(h.substring(0,16).getBytes("UTF-8"),"AES"),new IvParameterSpec(h.substring(16).getBytes("UTF-8")));return c.doFinal(input);
    }
    // Both formats are used in published clients: original Node uses form content type;
    // the .NET port uses octet-stream; Rust clients differ in the JSON uid type.
    static final String[] CLOUD_FORMATS={"S-F","N-F","S-B","N-B"};
    static boolean numericUid(String mode){return mode.startsWith("N-");}
    static String cloudContentType(String mode){return mode.endsWith("-F")?"application/x-www-form-urlencoded":"application/octet-stream";}
    static JSONObject identityForMode(JSONObject a,String key,String mode) throws Exception{
        JSONObject j=cloudIdentity(a,key);
        if(numericUid(mode))j.put("uid",new BigInteger(a.optString("userid")));
        return j;
    }
    static String cloudPlaintext(int page) throws Exception{
        if(page<1)throw new IOException("云盘页码无效");
        // Keep the exact property ordering used by JSON.stringify in the original module.
        return "{\"page\":"+page+",\"pagesize\":"+CLOUD_PAGE_SIZE+",\"getkmr\":1}";
    }
    static String identityPlaintext(JSONObject account,String key,String mode) throws Exception{
        JSONObject j=identityForMode(account,key,mode);
        return "{\"aes\":"+JSONObject.quote(j.getString("aes"))+",\"uid\":"+(numericUid(mode)?j.get("uid").toString():JSONObject.quote(j.getString("uid")))+",\"token\":"+JSONObject.quote(j.getString("token"))+"}";
    }
    static String cloudCode(JSONObject r){return Track.first(r,"error_code","err_code","errcode","code");}
    static boolean cloudOk(JSONObject r){
        String code=cloudCode(r);return r.optInt("status",0)==1&&(code.isEmpty()||"0".equals(code));
    }
    static String safeCloudNote(JSONObject r){
        String raw=Track.first(r,"errmsg","error_msg","msg","message");JSONObject d=r.optJSONObject("data");
        if(raw.isEmpty()&&d!=null)raw=Track.first(d,"errmsg","error_msg","msg","message");
        if(raw.equalsIgnoreCase("request params invalid")||raw.equalsIgnoreCase("invalid params"))return "参数校验拒绝";
        if(raw.equalsIgnoreCase("token invalid")||raw.equalsIgnoreCase("invalid token"))return "接口报告令牌无效";
        return "";
    }
    static class CloudReply {
        final JSONObject body;final String encoding;
        CloudReply(JSONObject b,String e){body=b;encoding=e;}
    }
    static CloudReply decodeCloud(byte[] response,String key) throws Exception{
        try{return new CloudReply(new JSONObject(new String(aes(Cipher.DECRYPT_MODE,response,key),"UTF-8")),"加密响应");}
        catch(Exception ignored){
            try{return new CloudReply(new JSONObject(new String(response,"UTF-8")),"明文响应");}
            catch(JSONException e){throw new IOException("云盘响应无法识别，接口可能已变更");}
        }
    }
    static class CloudFrame {
        final TreeMap<String,String> params;final byte[] body;final Map<String,String> headers;final String key;
        CloudFrame(TreeMap<String,String> p,byte[] b,Map<String,String> h,String k){params=p;body=b;headers=h;key=k;}
    }
    static CloudFrame cloudFrame(JSONObject account,int page,String mode,String mid,String now,String key,PublicKey publicKey) throws Exception{
        return cloudFrame(account,page,mode,mid,now,key,publicKey,null);
    }
    static CloudFrame cloudFrame(JSONObject account,int page,String mode,String mid,String now,String key,PublicKey publicKey,String bodyOverride) throws Exception{
        if(!Arrays.asList(CLOUD_FORMATS).contains(mode))throw new IOException("未知云盘请求格式");
        byte[] body=aes(Cipher.ENCRYPT_MODE,(bodyOverride==null?cloudPlaintext(page):bodyOverride).getBytes("UTF-8"),key);
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,publicKey);
        TreeMap<String,String> p=new TreeMap<String,String>();p.put("clienttime",now);p.put("mid",mid);p.put("key",md5(APP_ID+ANDROID_SALT+CLIENT_VERSION+now));p.put("clientver",CLIENT_VERSION);p.put("appid",APP_ID);p.put("p",hex(rsa.doFinal(identityPlaintext(account,key,mode).getBytes("UTF-8"))).toUpperCase(Locale.US));
        Map<String,String> headers=new HashMap<String,String>();headers.put("Content-Type",cloudContentType(mode));headers.put("Accept","application/json, text/plain, */*");
        return new CloudFrame(p,body,headers,key);
    }
    static final String[] CLOUD_ROUTES={"D","G"};
    static String formatOf(String label){int i=label.indexOf('/');return i<0?label:label.substring(i+1);}
    static String routeOf(String label){int i=label.indexOf('/');return i<0?"D":label.substring(0,i);}
    private CloudReply cloudAttempt(JSONObject account,int page,String label) throws Exception{
        String key=UUID.randomUUID().toString().replace("-","").substring(0,6);
        String route=routeOf(label);
        CloudFrame frame=cloudFrame(account,page,formatOf(label),mid,String.valueOf(System.currentTimeMillis()/1000),key,publicKey());
        byte[] response;
        if("G".equals(route)){
            // Same path other working endpoints use: gateway + x-router + Android signature over query and body.
            TreeMap<String,String> gp=new TreeMap<String,String>(frame.params);
            gp.put("dfid","-");gp.put("uuid","-");
            if(!account.optString("token").isEmpty())gp.put("token",account.optString("token"));
            if(!account.optString("userid").isEmpty())gp.put("userid",account.optString("userid"));
            Map<String,String> h=new HashMap<String,String>(frame.headers);h.put("x-router","mcloudservice.kugou.com");
            response=WireClient.request("https://gateway.kugou.com","/v1/get_list",gp,frame.body,ANDROID_SALT,false,mid,h);
        }else{
            response=WireClient.request("https://mcloudservice.kugou.com","/v1/get_list",frame.params,frame.body,null,false,mid,frame.headers);
        }
        return decodeCloud(response,key);
    }
    static String[] formatOrder(String preferred){
        java.util.LinkedHashSet<String> all=new java.util.LinkedHashSet<String>();
        for(String route:CLOUD_ROUTES)for(String f:CLOUD_FORMATS)all.add(route+"/"+f);
        java.util.LinkedHashSet<String> modes=new java.util.LinkedHashSet<String>();
        if(all.contains(preferred))modes.add(preferred);
        modes.addAll(all);return modes.toArray(new String[0]);
    }

    // Diagnostic-only: one direct request with a modified account/body, to see which change moves the error code.
    private String probe(String name,JSONObject account,String body){
        try{
            String key=UUID.randomUUID().toString().replace("-","").substring(0,6);
            CloudFrame f=cloudFrame(account,1,"S-F",mid,String.valueOf(System.currentTimeMillis()/1000),key,publicKey(),body);
            CloudReply r=decodeCloud(WireClient.request("https://mcloudservice.kugou.com","/v1/get_list",f.params,f.body,null,false,mid,f.headers),key);
            return name+": "+(cloudOk(r.body)?"成功":cloudCode(r.body).isEmpty()?"无代码":cloudCode(r.body))+" · "+redact(r.body.toString(),store.read());
        }catch(Exception e){return name+": 异常 "+(e.getMessage()==null?"":e.getMessage());}
    }
    static String redact(String raw,JSONObject a){
        String t=raw;
        for(String k:new String[]{"token","userid"}){String v=a.optString(k);if(v.length()>3)t=t.replace(v,"***");}
        return t.length()>200?t.substring(0,200)+"…":t;
    }
    String probeReport(JSONObject a){
        StringBuilder b=new StringBuilder("探针（仅诊断，不含令牌）：");
        try{
            JSONObject bad=new JSONObject(a.toString());bad.put("token","x"+a.optString("token"));
            JSONObject anon=new JSONObject(a.toString());anon.put("userid","1");
            b.append("\n").append(probe("正常",a,null));
            b.append("\n").append(probe("错误令牌",bad,null));
            b.append("\n").append(probe("其他uid",anon,null));
            b.append("\n").append(probe("无getkmr",a,"{\"page\":1,\"pagesize\":30}"));
            b.append("\n").append(probe("pagesize20",a,"{\"page\":1,\"pagesize\":20,\"getkmr\":1}"));
        }catch(Exception e){b.append("\n探针失败");}
        return b.toString();
    }
    static class EndOfList extends IOException{EndOfList(){super("云盘列表已到末尾");}}
    public JSONObject cloudPage(int page) throws Exception{return cloudPage(page,false);}
    // follow=true: later pages reuse the format that worked on page 1; 20010 there means "past the end", not failure.
    JSONObject cloudPage(int page,boolean follow) throws Exception{
        JSONObject account=store.read();if(!store.loggedIn())throw new IOException("请先登录酷狗账号");
        SharedPreferences pref=ctx.getSharedPreferences("cloud_format",0);String preferred=pref.getString("mode","");
        if(follow){
            JSONObject fr=cloudAttempt(account,page,formatOrder(preferred)[0]).body;
            if(cloudOk(fr))return fr;
            if("20010".equals(cloudCode(fr)))throw new EndOfList();
            throw apiError(fr,"云盘接口拒绝请求");
        }
        StringBuilder failures=new StringBuilder();
        for(String mode:formatOrder(preferred)){
            if(Thread.currentThread().isInterrupted())throw new IOException("操作已取消");
            CloudReply reply;
            try{reply=cloudAttempt(account,page,mode);}
            catch(IOException ex){
                if(!"G".equals(routeOf(mode)))throw ex;
                if(failures.length()>0)failures.append("\n");
                failures.append(mode).append(": ").append(ex.getMessage()==null?"请求失败":ex.getMessage());continue;
            }
            JSONObject r=reply.body;
            if(cloudOk(r)){pref.edit().putString("mode",mode).apply();return r;}
            String code=cloudCode(r);if(!"20010".equals(code))throw apiError(r,"云盘接口拒绝请求");
            if(failures.length()>0)failures.append("\n");
            failures.append(mode).append(": ").append(code).append(" · ").append(reply.encoding);
            String note=safeCloudNote(r);if(!note.isEmpty())failures.append(" · ").append(note);
        }
        String report="云盘"+formatOrder("").length+"种请求方式均未通过：\n"+failures+"\n"+sessionDiagnostic()+"\n"+probeReport(account)+"\n版本 1.0.6；当前登录已保留，不必反复扫码。";
        ctx.getSharedPreferences("diagnostic",0).edit().putString("last",report).apply();throw new IOException(report);
    }
    public ArrayList<Track> cloud() throws Exception{
        ArrayList<Track> list=new ArrayList<Track>();Set<String> seen=new HashSet<String>();
        for(int page=1;page<=1000;page++){
            if(Thread.currentThread().isInterrupted())throw new IOException("操作已取消");
            JSONObject r;try{r=cloudPage(page,page>1);}catch(EndOfList end){break;}
            int before=list.size();Track.collect(r,list,seen);
            int added=list.size()-before;JSONObject data=r.optJSONObject("data");int expected=data==null?-1:data.optInt("list_count",-1);
            if(added==0){if(expected>list.size())throw new IOException("云盘分页数据格式发生变化，请更新客户端");break;}
            if(expected>=0){if(list.size()>=expected)break;}else if(added<CLOUD_PAGE_SIZE)break;
            if(page==1000)throw new IOException("云盘超过三万首，请精简后刷新");
        }return list;
    }
    public String playUrl(Track t) throws Exception{
        TreeMap<String,String> p=defaults();String h=t.hash.toLowerCase(Locale.US);
        p.put("hash",h);p.put("ssa_flag","is_fromtrack");p.put("version","20102");p.put("ssl","1");p.put("album_audio_id",t.albumId.isEmpty()?"0":t.albumId);p.put("audio_id",t.audioId.isEmpty()?"0":t.audioId);
        p.put("pid","20026");p.put("kv_id","2");p.put("key",md5("musicclound"+h+"20026ebd1ac3134c880bda6a2194537843caa0162e2e7"));p.put("bucket","musicclound");p.put("name",Track.first(t.source,"name","filename","file_name"));p.put("with_res_tag","0");
        JSONObject r=json("https://gateway.kugou.com","/bsstrackercdngz/v2/query_musicclound_url",p,ANDROID_SALT);
        String url=findUrl(r);if(url.isEmpty())throw apiError(r,"这首歌暂时没有可播放地址");return url;
    }
    static String findUrl(Object o){
        if(o instanceof String){String s=(String)o;return s.startsWith("https://")||s.startsWith("http://")?s:"";}
        if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int i=0;i<a.length();i++){String u=findUrl(a.opt(i));if(!u.isEmpty())return u;}}
        if(o instanceof JSONObject){JSONObject j=(JSONObject)o;for(String k:new String[]{"url","backup_url","backupurl","play_url","data"}){String u=findUrl(j.opt(k));if(!u.isEmpty())return u;}}return "";
    }
    static IOException apiError(JSONObject j,String fallback){String code=Track.first(j,"error_code","err_code","errcode","code");return new ApiException(fallback+(code.isEmpty()?"":"（代码 "+code+"）"),code);}
}
