package com.cloudtune.player;
import org.json.*;
import java.util.*;
public class Track {
    public String hash,name,artist,albumId,audioId;
    public JSONObject source;
    static String first(JSONObject j,String...keys){for(String k:keys){String s=j.optString(k,"");if(!s.isEmpty()&&!s.equals("null"))return s;}return "";}
    public Track(JSONObject j){
        source=j; hash=first(j,"hash","filehash","file_hash","hash_std");
        name=first(j,"name","filename","file_name","songname","song_name");
        artist=first(j,"author_name","singername","singer","author");
        if(name.isEmpty()) name="未命名音乐";
        if(artist.isEmpty() && name.contains(" - ")){int n=name.indexOf(" - ");artist=name.substring(0,n);name=name.substring(n+3);}
        name=name.replaceFirst("(?i)\\.(mp3|flac|wav|ape|m4a|ogg|aac)$","");
        if(artist.isEmpty())artist="我的酷狗云盘";
        albumId=first(j,"album_audio_id");audioId=first(j,"audio_id");
    }
    public boolean isLocal(){return hash!=null&&hash.startsWith("local:")&&!source.optString("local_path").isEmpty();}
    public String localPath(){return source.optString("local_path");}
    public static void collect(Object o,List<Track> result,Set<String> seen){
        if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int i=0;i<a.length();i++)collect(a.opt(i),result,seen);}
        else if(o instanceof JSONObject){JSONObject j=(JSONObject)o;
            String hash=first(j,"hash","filehash","file_hash","hash_std");
            if(!hash.isEmpty() && !first(j,"name","filename","file_name","songname","song_name").isEmpty()){
                if(seen.add(hash.toLowerCase(java.util.Locale.US)))result.add(new Track(j));
            }else{Iterator<String> it=j.keys();while(it.hasNext()) collect(j.opt(it.next()),result,seen);}
        }
    }
}
