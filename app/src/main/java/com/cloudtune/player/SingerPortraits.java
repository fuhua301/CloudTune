package com.cloudtune.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Kugou's author-image service; no login token or account ID is needed. */
final class SingerPortraits {
    static TreeMap<String,String> params(Track t) throws Exception {
        JSONObject item=new JSONObject();item.put("audio_id",t.audioId.isEmpty()?0:t.audioId);item.put("hash",t.hash);
        item.put("album_audio_id",t.albumId.isEmpty()?0:t.albumId);item.put("filename",t.artist+" - "+t.name);
        TreeMap<String,String> p=new TreeMap<String,String>();p.put("appid",KugouApi.APP_ID);p.put("clientver",KugouApi.CLIENT_VERSION);
        p.put("count","5");p.put("data",new JSONArray().put(item).toString());p.put("isCdn","1");p.put("publish_time","1");p.put("show_authors","1");return p;
    }
    interface Source {
        JSONObject audio(Track track) throws Exception;
        JSONObject search(String name) throws Exception;
        JSONObject author(String id) throws Exception;
    }
    private static final Map<String,ArrayList<String>> artistCache=new LinkedHashMap<String,ArrayList<String>>();
    static TreeMap<String,String> searchParams(String name){
        TreeMap<String,String> p=new TreeMap<String,String>();p.put("appid",KugouApi.APP_ID);p.put("clientver",KugouApi.CLIENT_VERSION);
        p.put("clienttime",String.valueOf(System.currentTimeMillis()/1000));p.put("mid","1234567890");p.put("dfid","-");p.put("uuid","-");
        p.put("albumhide","0");p.put("iscorrection","1");p.put("keyword",name);p.put("nocollect","0");p.put("page","1");p.put("pagesize","10");p.put("platform","AndroidFilter");return p;
    }
    static final Source HTTP=new Source(){
        public JSONObject audio(Track t) throws Exception{return json(WireClient.request("https://expendablekmr.kugou.com","/v2/author_image/audio",params(t),null,KugouApi.ANDROID_SALT,false,"-",null));}
        public JSONObject search(String name) throws Exception{
            Map<String,String> headers=new HashMap<String,String>();headers.put("x-router","complexsearch.kugou.com");
            return json(WireClient.request("https://gateway.kugou.com","/v1/search/author",searchParams(name),null,KugouApi.ANDROID_SALT,false,"1234567890",headers));
        }
        public JSONObject author(String id) throws Exception{
            TreeMap<String,String> p=new TreeMap<String,String>();p.put("fields_pack","allimages");p.put("authorimg_type","2,3,4,6");p.put("entity_id",id);
            return json(WireClient.request("https://openapicdnretry.kugou.com","/kmr/v1/author/extend",p,null,null,false,"-",null));
        }
    };
    private static JSONObject json(byte[] bytes) throws Exception{
        JSONObject j=new JSONObject(new String(bytes,"UTF-8"));if(j.optInt("status",1)==0||j.optInt("error_code",0)!=0)throw new IOException("歌手图片接口暂不可用");return j;
    }
    static ArrayList<String> names(String artist){
        LinkedHashSet<String> out=new LinkedHashSet<String>();
        if(artist!=null&&!artist.equals("我的酷狗云盘")&&!artist.equals("本地音乐"))for(String name:artist.split("(?i)\\s*(?:[、,，;；&＆+＋]|\\s+(?:feat\\.?|ft\\.?)\\s+)\\s*")){
            name=name.replaceAll("<[^>]*>","").trim();if(!name.isEmpty()&&out.size()<4)out.add(name);
        }
        return new ArrayList<String>(out);
    }
    static String nameKey(String name){return java.text.Normalizer.normalize(name.replaceAll("<[^>]*>",""),java.text.Normalizer.Form.NFKC).replaceAll("\\s+","").toLowerCase(Locale.US);}
    static JSONObject matchAuthor(JSONObject response,String requested){
        JSONObject data=response.optJSONObject("data");JSONArray list=data==null?null:data.optJSONArray("lists");
        JSONObject best=null;long score=-1;
        if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject candidate=list.optJSONObject(i);if(candidate==null)continue;
            String name=Track.first(candidate,"AuthorName","author_name","singername"),id=Track.first(candidate,"AuthorId","author_id","singerid");
            if(!nameKey(requested).equals(nameKey(name))||!id.matches("[1-9][0-9]*"))continue;
            long rank=(candidate.optInt("IsSettledAuthor",0)>0?1000000000000L:0)+Math.min(999999999999L,Math.max(0,candidate.optLong("Heat",0)));
            if(rank>score){score=rank;best=candidate;}
        }
        return best;
    }
    static ArrayList<String> urls(final Track track) throws Exception {
        return resolve(track,new Source(){
            public JSONObject audio(Track t) throws Exception{return HTTP.audio(t);}
            public JSONObject search(String name) throws Exception{return HTTP.search(name);}
            public JSONObject author(String id) throws Exception{
                synchronized(artistCache){ArrayList<String> cached=artistCache.get(id);if(cached!=null){JSONArray images=new JSONArray();for(String url:cached)images.put(url);return new JSONObject().put("data",images);}}
                JSONObject response=HTTP.author(id);ArrayList<String> urls=parse(response);
                if(!urls.isEmpty())synchronized(artistCache){if(artistCache.size()>=64)artistCache.remove(artistCache.keySet().iterator().next());artistCache.put(id,urls);}
                return response;
            }
        });
    }
    static boolean portrait(String url){return !url.contains("/softhead/")&&!url.contains("/head/");}
    static void addUrls(Collection<String> urls,Set<String> photos,Set<String> avatars){for(String url:urls)if(portrait(url))photos.add(url);else avatars.add(url);}
    static void interrupted() throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    static ArrayList<String> resolve(Track track,Source source) throws Exception {
        LinkedHashSet<String> photos=new LinkedHashSet<String>(),avatars=new LinkedHashSet<String>();Exception failure=null;
        try{if(!track.isLocal())addUrls(parse(source.audio(track)),photos,avatars);}catch(Exception e){failure=e;}
        interrupted();
        if(photos.isEmpty())for(String name:names(track.artist)){
            interrupted();
            try{
                JSONObject match=matchAuthor(source.search(name),name);if(match==null)continue;
                String avatar=normalize(Track.first(match,"Avatar","avatar","sizable_avatar"));if(!avatar.isEmpty())avatars.add(avatar);
                String id=Track.first(match,"AuthorId","author_id","singerid");
                addUrls(parse(source.author(id)),photos,avatars);
            }catch(Exception e){failure=e;}
        }
        interrupted();photos.addAll(avatars);
        if(photos.isEmpty()&&failure!=null)throw new IOException("歌手图片查询失败",failure);
        return new ArrayList<String>(photos);
    }
    static ArrayList<String> parse(Object value){
        LinkedHashSet<String> out=new LinkedHashSet<String>();collect(value,out,false);collect(value,out,true);return new ArrayList<String>(out);
    }
    private static void collect(Object value,Set<String> out,boolean avatars){
        if(out.size()>=24)return;
        if(value instanceof JSONObject){
            JSONObject j=(JSONObject)value;Object images=j.opt("imgs");
            if(images instanceof JSONObject){for(String type:new String[]{"6","4","3","5","2"})collect(((JSONObject)images).opt(type),out,avatars);}
            else if(images instanceof JSONArray){
                JSONArray array=(JSONArray)images;
                for(int type:new int[]{6,4,3,5,2})for(int i=0;i<array.length();i++){JSONObject image=array.optJSONObject(i);if(image!=null&&image.optInt("type")==type)collect(image,out,avatars);}
            }
            Iterator<String> keys=j.keys();while(keys.hasNext()){
                String k=keys.next(),lower=k.toLowerCase(Locale.US);if(k.equals("imgs")||lower.contains("album")||(!avatars&&lower.contains("avatar")))continue;
                collect(j.opt(k),out,avatars);
            }
        }else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)collect(array.opt(i),out,avatars);}
        else if(value instanceof String){String url=normalize((String)value);if(!url.isEmpty())out.add(url);}
    }
    static String normalize(String raw){
        try{
            String s=raw.replace("{size}","1080").replace("{width}","1080").replace("{height}","1920");
            if(s.startsWith("//"))s="https:"+s;if(s.startsWith("http://"))s="https://"+s.substring(7);
            URL u=new URL(s);String host=u.getHost().toLowerCase(Locale.US);
            return "https".equals(u.getProtocol())&&(host.equals("kugou.com")||host.endsWith(".kugou.com"))&&u.getUserInfo()==null?s:"";
        }catch(Exception e){return "";}
    }
    static Bitmap load(Context context,String url) throws Exception {
        String safe=normalize(url);if(safe.isEmpty())throw new IOException("写真地址不可用");
        File dir=new File(context.getCacheDir(),"portraits");dir.mkdirs();File file=new File(dir,KugouApi.md5(safe)+".img");
        if(file.isFile()){Bitmap b=decode(file);if(b!=null){file.setLastModified(System.currentTimeMillis());return b;}file.delete();}
        HttpURLConnection c=(HttpURLConnection)new URL(safe).openConnection();
        File temp=File.createTempFile("portrait-",".part",dir);
        try{
            c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Android KugouMusic");
            if(c.getResponseCode()!=200)throw new IOException("写真下载失败");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){
                byte[] bytes=new byte[8192];int n,total=0;while((n=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>10000000)throw new IOException("写真文件过大");out.write(bytes,0,n);}
            }
            Bitmap bitmap=decode(temp);if(bitmap==null)throw new IOException("写真格式不可用");
            if(!temp.renameTo(file))temp.delete();trim(dir,file);return bitmap;
        }finally{c.disconnect();temp.delete();}
    }
    private static Bitmap decode(File f){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        if(o.outWidth<=0||o.outHeight<=0||o.outWidth>20000||o.outHeight>20000)return null;
        o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1600)o.inSampleSize*=2;
        o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(f.getPath(),o);
    }
    private static void trim(File dir,File keep){
        File[] all=dir.listFiles();if(all==null)return;Arrays.sort(all,new Comparator<File>(){public int compare(File a,File b){return Long.compare(a.lastModified(),b.lastModified());}});
        long size=0;for(File f:all)size+=f.length();for(File f:all)if(size>24000000&&!f.equals(keep)){long n=f.length();if(f.delete())size-=n;}
    }
}
