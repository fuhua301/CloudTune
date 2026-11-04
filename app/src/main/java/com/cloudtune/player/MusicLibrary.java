package com.cloudtune.player;
import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;
public class MusicLibrary {
    static File file(Context c){return new File(c.getFilesDir(),"cloud.json");}
    public static synchronized ArrayList<Track> read(Context c){
        ArrayList<Track> list=new ArrayList<Track>();
        try{FileInputStream in=new FileInputStream(file(c));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();JSONObject j=new JSONObject(new String(out.toByteArray(),"UTF-8"));
            if(!j.optString("userid").equals(new AccountStore(c).read().optString("userid")))return list;
            JSONArray a=j.getJSONArray("tracks");for(int i=0;i<a.length();i++)list.add(new Track(a.getJSONObject(i)));
        }catch(Exception ignored){}return list;
    }
    public static synchronized void save(Context c,ArrayList<Track> list) throws Exception{
        JSONArray a=new JSONArray();for(Track t:list)a.put(t.source);JSONObject j=new JSONObject();j.put("userid",new AccountStore(c).read().optString("userid"));j.put("tracks",a);
        File tmp=new File(c.getFilesDir(),"cloud.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(j.toString().getBytes("UTF-8"));out.getFD().sync();}
        if(!tmp.renameTo(file(c)))throw new IOException("无法保存云盘列表");
    }
    public static void clear(Context c){file(c).delete();new File(c.getFilesDir(),"cloud.tmp").delete();c.getSharedPreferences("player",0).edit().clear().commit();}
}
