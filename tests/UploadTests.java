package com.cloudtune.player;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;

public class UploadTests {
    static int checks;static KeyPair keys;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static JSONObject account()throws Exception{return new JSONObject().put("userid","123").put("token","test-only").put("appid",KugouApi.APP_ID).put("auth_version",KugouApi.AUTH_VERSION);}
    static class Fake implements CloudUploader.Transport {
        int parts,adds,completes;long sent;boolean instant,badHost,cancelPart,failAdd;AtomicBoolean stop;JSONObject added;
        public byte[] request(String base,String path,TreeMap<String,String> params,byte[] body,Map<String,String> headers)throws Exception{
            JSONObject result=new JSONObject().put("status",1);
            if(path.endsWith("/audio"))result.put("data",new JSONArray());
            else if(path.endsWith("/auth"))result.put("data",new JSONObject().put("authorization","AUTH"));
            else if(path.endsWith("/initiate/music")){JSONObject d=new JSONObject().put("x-bss-filename","server-hash");if(!instant)d.put("upload_id","UPLOAD").put("external_host",badHost?"evil.example":"bssulbig.kugou.com");else d.put("x-bss-hash","already-present");result.put("data",d);}
            else if(path.endsWith("/upload")){parts++;sent+=body.length;check(body.length<=1048576,"bounded upload chunks");check(String.valueOf(parts).equals(params.get("partnumber")),"sequential parts");if(cancelPart)stop.set(true);}
            else if(path.endsWith("/complete")){completes++;check(String.valueOf(parts).equals(params.get("partnumber")),"final part count");result.put("data",new JSONObject().put("x-bss-filename","final-hash"));}
            else if(path.endsWith("/add_files")){
                adds++;Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,keys.getPrivate());String hex=params.get("p");byte[] bytes=new byte[hex.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
                JSONObject identity=new JSONObject(new String(rsa.doFinal(bytes),"UTF-8"));check(identity.get("uid") instanceof String,"UID encoded as string");
                added=new JSONObject(new String(KugouApi.aes(Cipher.DECRYPT_MODE,body,identity.getString("aes")),"UTF-8"));if(failAdd)result.put("status",0).put("error_code",500);
            }else throw new AssertionError(path);
            if(params.containsKey("signature")){TreeMap<String,String> unsigned=new TreeMap<String,String>(params);String sign=unsigned.remove("signature");check(sign.equals(WireClient.signature(unsigned,null,KugouApi.ANDROID_SALT,false)),"BSS signs query without chunk body");}
            return result.toString().getBytes("UTF-8");
        }
    }
    static CloudUploader uploader(Fake fake)throws Exception{return new CloudUploader(account(),"1234567890",keys.getPublic(),fake,new CloudUploader.Progress(){public void update(String stage,int percent){check(percent>=0&&percent<=100,"progress bounded");}},fake.stop);}
    public static void main(String[] args)throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(1024);keys=generator.generateKeyPair();Path file=Files.createTempFile("cloud-audio-test-",".mp3");
        try{
            byte[] bytes=new byte[2*1048576+13];new Random(42).nextBytes(bytes);Files.write(file,bytes);String md5=KugouApi.hex(MessageDigest.getInstance("MD5").digest(bytes));
            Track track=new Track(new JSONObject().put("hash","local:"+md5).put("filename","歌手 - 测试.mp3").put("local_path",file.toString()).put("local_md5",md5).put("local_ext","mp3").put("local_duration",1234));
            Fake normal=new Fake();normal.stop=new AtomicBoolean();uploader(normal).upload(track);check(normal.parts==3&&normal.sent==bytes.length&&normal.completes==1&&normal.adds==1,"multipart completes and adds exactly once");JSONObject item=normal.added.getJSONArray("data").getJSONObject(0);check(item.getLong("size")==bytes.length&&item.getLong("timelen")==1234,"numeric file metadata");check(item.getString("hash").equals("final-hash"),"use completed BSS hash");
            Fake instant=new Fake();instant.stop=new AtomicBoolean();instant.instant=true;uploader(instant).upload(track);check(instant.parts==0&&instant.completes==0&&instant.adds==1,"instant upload skips byte transfer");
            Fake cancel=new Fake();cancel.stop=new AtomicBoolean();cancel.cancelPart=true;boolean canceled=false;try{uploader(cancel).upload(track);}catch(InterruptedIOException e){canceled=true;}check(canceled&&cancel.adds==0&&cancel.parts==1,"cancel blocks cloud registration");
            Fake host=new Fake();host.stop=new AtomicBoolean();host.badHost=true;boolean rejected=false;try{uploader(host).upload(track);}catch(IOException e){rejected=true;}check(rejected&&host.parts==0&&host.adds==0,"untrusted host never receives token or file");
            Fake lost=new Fake();lost.stop=new AtomicBoolean();lost.failAdd=true;boolean failed=false;try{uploader(lost).upload(track);}catch(IOException e){failed=true;}check(failed&&lost.adds==1,"registration failure never automatically retries");
            check(CloudUploader.trustedHost("http://bssulbig.kugou.com").equals("https://bssulbig.kugou.com"),"HTTPS upload host");
            check(track.isLocal()&&track.localPath().equals(file.toString()),"local routing model");
        }finally{Files.deleteIfExists(file);}
        System.out.println("PASS: "+checks+" local/upload checks; multipart, instant upload, AES/RSA, cancellation, hosts and no mutation retries");
    }
}
