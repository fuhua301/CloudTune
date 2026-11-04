package com.cloudtune.player;

import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Lyrics from Kugou's public lyric service (no login). Results are cached under the app cache directory. */
public class Lyrics {
    public static class Line {
        public final int ms;public final String text;
        Line(int m,String t){ms=m;text=t;}
    }
    static final Pattern TAG=Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    static final String SEARCH="https://lyrics.kugou.com/search?ver=1&man=yes&client=pc";
    static final String DOWNLOAD="https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8";

    static final Pattern OFFSET=Pattern.compile("\\[offset:\\s*(-?\\d+)\\s*\\]",Pattern.CASE_INSENSITIVE);
    public static ArrayList<Line> parse(String lrc){
        ArrayList<Line> out=new ArrayList<Line>();
        if(lrc==null)return out;
        if(lrc.startsWith("\uFEFF"))lrc=lrc.substring(1);
        int shift=0;Matcher om=OFFSET.matcher(lrc);if(om.find()){try{shift=Integer.parseInt(om.group(1));}catch(Exception ignored){}}
        for(String raw:lrc.split("\\r?\\n")){
            ArrayList<Integer> times=new ArrayList<Integer>();int pos=0;
            while(true){
                Matcher m=TAG.matcher(raw);m.region(pos,raw.length());
                if(!m.lookingAt())break;
                int frac=0;String f=m.group(3);
                if(f!=null){frac=Integer.parseInt(f);if(f.length()==1)frac*=100;else if(f.length()==2)frac*=10;}
                times.add(Math.max(0,Integer.parseInt(m.group(1))*60000+Integer.parseInt(m.group(2))*1000+frac-shift));
                pos=m.end();
            }
            if(times.isEmpty())continue;
            String text=raw.substring(pos).trim();
            if(text.isEmpty())continue;
            for(int t:times)out.add(new Line(t,text));
        }
        Collections.sort(out,new Comparator<Line>(){public int compare(Line a,Line b){return a.ms<b.ms?-1:a.ms>b.ms?1:0;}});
        return out;
    }

    /** Returns lines (empty if no lyrics exist). pick selects among candidate versions (0 = closest to the track's length). */
    public static ArrayList<Line> load(Context c,Track t,int durationMs,int pick) throws IOException{
        File dir=new File(c.getCacheDir(),"lyrics");dir.mkdirs();
        File f=new File(dir,t.hash.replaceAll("[^0-9a-zA-Z]","")+"_"+pick+".lrc");
        if(f.isFile()){
            try{String cached=new String(readAll(new FileInputStream(f)),"UTF-8");ArrayList<Line> l=parse(cached);if(!l.isEmpty())return l;}catch(IOException ignored){}
        }
        String lrc=fetch(t,durationMs,pick);
        ArrayList<Line> lines=parse(lrc);
        if(!lines.isEmpty()){
            try{FileOutputStream o=new FileOutputStream(f);try{o.write(lrc.getBytes("UTF-8"));}finally{o.close();}}catch(IOException ignored){}
        }
        return lines;
    }

    static String fetch(Track t,int durationMs,int pick) throws IOException{
        String kw=("我的酷狗云盘".equals(t.artist)||t.artist==null||t.artist.isEmpty())?t.name:t.artist+" - "+t.name;
        String[] searches={t.isLocal()?SEARCH+"&keyword="+enc(kw)+"&duration="+durationMs:SEARCH+"&hash="+enc(t.hash.toLowerCase(Locale.US))+"&keyword="+enc(kw)+"&duration="+durationMs,
                           SEARCH+"&keyword="+enc(kw)+"&duration="+durationMs,
                           SEARCH+"&keyword="+enc(t.name)+"&duration="+durationMs};
        IOException last=null;
        for(String url:searches){
            try{
                JSONObject r=new JSONObject(http(url));
                JSONArray cand=r.optJSONArray("candidates");
                if(cand==null||cand.length()==0)continue;
                final ArrayList<JSONObject> list=new ArrayList<JSONObject>();
                for(int i=0;i<cand.length();i++){JSONObject o=cand.optJSONObject(i);if(o!=null)list.add(o);}
                if(list.isEmpty())continue;
                final int dur=durationMs;
                // Prefer the version whose length is closest to the file being played; stable sort keeps server score order otherwise.
                if(dur>0)Collections.sort(list,new Comparator<JSONObject>(){public int compare(JSONObject a,JSONObject b){return gap(a,dur)-gap(b,dur);}});
                for(int k=0;k<list.size()&&k<5;k++){
                    JSONObject o=list.get((pick+k)%list.size());
                    String id=o.optString("id"),key=o.optString("accesskey");
                    if(id.isEmpty()||key.isEmpty())continue;
                    JSONObject d=new JSONObject(http(DOWNLOAD+"&id="+enc(id)+"&accesskey="+enc(key)));
                    String content=d.optString("content");
                    if(content.isEmpty())continue;
                    String text=new String(Base64.decode(content,Base64.DEFAULT),"UTF-8");
                    if(!parse(text).isEmpty())return text;
                }
            }catch(JSONException e){last=new IOException("歌词接口返回异常");}
            catch(IOException e){last=e;}
        }
        if(last!=null&&isNetwork(last))throw last;
        return "";
    }
    static int gap(JSONObject o,int dur){int d=o.optInt("duration",0);return d<=0?Integer.MAX_VALUE/2:Math.abs(d-dur);}
    static boolean isNetwork(IOException e){return e instanceof SocketTimeoutException||e instanceof UnknownHostException||e instanceof ConnectException||(e.getMessage()!=null&&e.getMessage().startsWith("HTTP "));}
    static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(UnsupportedEncodingException e){return s;}}
    static String http(String url) throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 10)");
            int s=c.getResponseCode();if(s!=200)throw new IOException("HTTP "+s);
            return new String(readAll(c.getInputStream()),"UTF-8");
        }finally{c.disconnect();}
    }
    static byte[] readAll(InputStream in) throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new IOException("歌词过大");}}finally{in.close();}
        return out.toByteArray();
    }
}
