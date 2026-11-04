package com.cloudtune.player;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;

/** Direct Kugou multipart upload, then encrypted add_files; file bytes stay in 1 MiB chunks. */
final class CloudUploader {
    interface Progress {void update(String stage,int percent);}
    interface Transport {byte[] request(String base,String path,TreeMap<String,String> params,byte[] body,Map<String,String> headers)throws Exception;}
    final JSONObject account;final String mid;final PublicKey publicKey;final Transport transport;final AtomicBoolean cancel;final Progress progress;
    CloudUploader(JSONObject a,String m,PublicKey key,Transport t,Progress p,AtomicBoolean stop){account=a;mid=m;publicKey=key;transport=t;progress=p;cancel=stop;}
    void active()throws IOException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("上传已取消");}
    TreeMap<String,String> base(){TreeMap<String,String> p=KugouApi.baseParams(mid,String.valueOf(System.currentTimeMillis()/1000),account);p.put("version",KugouApi.CLIENT_VERSION);return p;}
    static String trustedHost(String host)throws Exception{
        if(host==null||host.isEmpty())throw new IOException("上传服务器地址为空");if(!host.contains("://"))host="https://"+host;
        URL url=new URL(host);String h=url.getHost().toLowerCase(Locale.US);if(!h.endsWith(".kugou.com")||url.getUserInfo()!=null||url.getPort()>0&&url.getPort()!=443)throw new IOException("酷狗返回了无法识别的上传服务器");
        return "https://"+h;
    }
    JSONObject bss(String host,String path,TreeMap<String,String> params,byte[] bytes,String authorization)throws Exception{
        active();params.put("signature",WireClient.signature(params,null,KugouApi.ANDROID_SALT,false));Map<String,String> h=new HashMap<String,String>();h.put("Content-Type","application/octet-stream");if(authorization!=null)h.put("Authorization",authorization);
        JSONObject reply=new JSONObject(new String(transport.request(host,path,params,bytes,h),"UTF-8"));active();
        if(reply.optInt("status",0)!=1)throw new IOException("酷狗拒绝上传（代码 "+reply.optInt("error_code",reply.optInt("errcode",0))+"）");return reply;
    }
    JSONObject match(String hash){
        try{active();JSONObject body=new JSONObject();body.put("appid",Integer.parseInt(KugouApi.APP_ID));body.put("clienttime",System.currentTimeMillis()/1000);body.put("clientver",Integer.parseInt(KugouApi.CLIENT_VERSION));body.put("mid",mid);body.put("dfid","-");body.put("key",KugouApi.md5(KugouApi.APP_ID+KugouApi.ANDROID_SALT+KugouApi.CLIENT_VERSION+body.get("clienttime")));body.put("data",new JSONArray().put(new JSONObject().put("hash",hash)));for(String k:new String[]{"show_privilege","show_author_alias","show_rel_album_audio_info","show_remarks"})body.put(k,0);
            Map<String,String> headers=new HashMap<String,String>();headers.put("x-router","kmr.service.kugou.com");headers.put("Content-Type","application/json");
            JSONObject reply=new JSONObject(new String(transport.request("https://gateway.kugou.com","/v2/album_audio/audio",new TreeMap<String,String>(),body.toString().getBytes("UTF-8"),headers),"UTF-8"));JSONArray data=reply.optJSONArray("data");Object candidate=data==null?null:data.opt(0);if(candidate instanceof JSONArray)candidate=((JSONArray)candidate).opt(0);return candidate instanceof JSONObject?(JSONObject)candidate:null;
        }catch(Exception ignored){return null;}
    }
    static JSONObject entry(Track track,String hash,JSONObject match,long size)throws Exception{
        JSONObject audio=match==null?null:match.optJSONObject("audio_info");String standard=audio==null?"":audio.optString("hash");if(standard.isEmpty()&&match!=null)standard=match.optString("hash");if(standard.isEmpty())standard=track.source.optString("local_md5");
        JSONObject item=new JSONObject();item.put("name",track.source.optString("filename",track.artist+" - "+track.name));item.put("ext",track.source.optString("local_ext","mp3"));item.put("author_name","本地音乐".equals(track.artist)?"":track.artist);item.put("hash",hash);item.put("hash_std",standard.toLowerCase(Locale.US));item.put("audio_id",audio==null?match==null?0:match.optLong("audio_id",0):audio.optLong("audio_id",0));item.put("album_audio_id",match==null?0:match.optLong("album_audio_id",0));item.put("bitrate",4);item.put("size",size);item.put("timelen",track.source.optLong("local_duration",0));return item;
    }
    void upload(Track track)throws Exception{
        active();if(!AccountStore.compatible(account))throw new IOException("请先登录酷狗账号");File file=new File(track.localPath());if(!track.isLocal()||!file.isFile()||file.length()==0)throw new IOException("本地音乐文件不可用");String md5=track.source.optString("local_md5");if(!md5.matches("[0-9a-fA-F]{32}"))throw new IOException("文件校验信息不可用");
        progress.update("匹配曲库信息",0);JSONObject matched=match(md5);active();String bucket="musicclound",ext=track.source.optString("local_ext","mp3");
        TreeMap<String,String> auth=base();auth.put("bucket",bucket);auth.put("filename",md5);auth.put("method","POST");auth.put("loginType","1");auth.put("buVerifyCode",KugouApi.md5(KugouApi.APP_ID+bucket+"8ae10344e9738dcb"));auth.put("extranet","1");progress.update("取得上传授权",1);
        JSONObject authorizationData=bss("https://gateway.kugou.com","/bsstrackercdngz/v1/upload/auth",auth,null,null).optJSONObject("data");String authorization=authorizationData==null?"":authorizationData.optString("authorization");if(authorization.isEmpty())throw new IOException("酷狗未返回上传授权");
        TreeMap<String,String> init=base();init.put("bucket",bucket);init.put("filename",md5);init.put("ssl","1");init.put("extendname",ext);init.put("authorization",authorization);
        JSONObject initData=bss("https://bssulbig.kugou.com","/v2/multipart/initiate/music",init,new byte[0],authorization).optJSONObject("data");if(initData==null)throw new IOException("初始化上传失败");String uploadId=initData.optString("upload_id"),hash=initData.optString("x-bss-filename",md5);
        if(!uploadId.isEmpty()){
            String host=trustedHost(initData.optString("external_host"));int part=0;long sent=0;byte[] chunk=new byte[1024*1024];
            try(InputStream in=new FileInputStream(file)){
                while(true){int filled=0,n;while(filled<chunk.length&&(n=in.read(chunk,filled,chunk.length-filled))!=-1)filled+=n;if(filled==0)break;active();part++;
                    TreeMap<String,String> p=base();p.put("bucket",bucket);p.put("authorization",authorization);p.put("filename",md5);p.put("partnumber",String.valueOf(part));p.put("upload_id",uploadId);p.put("body_empty","1");
                    bss(host,"/v3/multipart/upload",p,filled==chunk.length?chunk:Arrays.copyOf(chunk,filled),authorization);sent+=filled;progress.update("上传文件",(int)(sent*95/file.length()));
                }
            }
            TreeMap<String,String> complete=base();complete.put("bucket",bucket);complete.put("authorization",authorization);complete.put("filename",md5);complete.put("partnumber",String.valueOf(part));complete.put("upload_id",uploadId);complete.put("md5",md5);complete.put("if_id3","1");JSONObject done=bss(host,"/v3/multipart/complete",complete,new byte[0],authorization).optJSONObject("data");if(done!=null)hash=done.optString("x-bss-filename",hash);
        }else if(initData.optString("x-bss-hash").isEmpty()&&initData.optString("x-bss-filename").isEmpty())throw new IOException("酷狗未确认文件已存在，请重试");
        active();progress.update("添加到酷狗云盘",97);JSONObject payload=new JSONObject().put("data",new JSONArray().put(entry(track,hash,matched,file.length()))).put("list_ver",0);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,6);KugouApi.CloudFrame frame=KugouApi.cloudFrame(account,1,"S-F",mid,String.valueOf(System.currentTimeMillis()/1000),key,publicKey,payload.toString());
        // Never automatically retry this mutation: a lost reply could already have committed it.
        byte[] reply=transport.request("https://mcloudservice.kugou.com","/v1/add_files",frame.params,frame.body,frame.headers);JSONObject result=KugouApi.decodeCloud(reply,key).body;
        if(!KugouApi.cloudOk(result))throw new IOException("添加云盘失败（代码 "+KugouApi.cloudCode(result)+"），可刷新云盘确认后重试");progress.update("上传成功",100);
    }
}
