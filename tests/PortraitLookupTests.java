package com.cloudtune.player;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public class PortraitLookupTests {
    static int checks;
    static Path fixtures;
    static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    static JSONObject fixture(String prefix,int id)throws Exception{return new JSONObject(new String(Files.readAllBytes(fixtures.resolve(prefix+id+".json")),"UTF-8"));}
    static final LinkedHashMap<String,Integer> ids=new LinkedHashMap<String,Integer>();
    static class Fake implements SingerPortraits.Source {
        boolean failAudio,avatarOnly,failSearch;int searches,details;
        final ArrayList<String> queried=new ArrayList<String>();
        public JSONObject audio(Track track)throws Exception{
            if(failAudio)throw new IOException("unavailable");
            return avatarOnly?new JSONObject("{\"data\":[{\"sizable_avatar\":\"http://singerimg.kugou.com/uploadpic/softhead/{size}/fallback.jpg\"}]}"):new JSONObject("{\"status\":1,\"data\":[]}");
        }
        public JSONObject search(String name)throws Exception{searches++;queried.add(name);if(failSearch)throw new IOException("offline");Integer id=ids.get(name);return id==null?new JSONObject("{\"data\":{\"lists\":[]}}"):fixture("author-search-",id);}
        public JSONObject author(String id)throws Exception{details++;return fixture("author-detail-",Integer.parseInt(id));}
    }
    static Track track(String artist)throws Exception{return new Track(new JSONObject().put("hash","TEST_NO_SONG_MATCH").put("name","测试歌曲").put("author_name",artist));}
    public static void main(String[] args)throws Exception{
        fixtures=Paths.get(args[0]);ids.put("程响",4442);ids.put("蔡旻佑",432);ids.put("顾峰",983);ids.put("斯琴高丽",5837);
        for(Map.Entry<String,Integer> entry:ids.entrySet()){
            String name=entry.getKey();int id=entry.getValue();
            JSONObject chosen=SingerPortraits.matchAuthor(fixture("author-search-",id),name);check(chosen!=null&&chosen.optInt("AuthorId")==id,"correct artist "+name);
            ArrayList<String> photos=SingerPortraits.parse(fixture("author-detail-",id));check(!photos.isEmpty(),"detail photos "+name);check(!photos.get(0).contains("softhead"),"photo precedes avatar "+name);
            check(photos.get(0).contains(id==983?"mobile_portrait":"mobile_super_portrait"),"vertical priority "+name);
            Fake source=new Fake();ArrayList<String> resolved=SingerPortraits.resolve(track(name),source);check(!resolved.isEmpty(),"fallback from empty song data "+name);check(source.searches==1&&source.details==1,"fallback actually called "+name);
        }
        Fake duet=new Fake();SingerPortraits.resolve(track("顾峰、 斯琴高丽"),duet);check(duet.queried.equals(Arrays.asList("顾峰","斯琴高丽"))&&duet.details==2,"both duet artists queried");
        Fake failingAudio=new Fake();failingAudio.failAudio=true;check(!SingerPortraits.resolve(track("程响"),failingAudio).isEmpty(),"track endpoint exception does not block singer fallback");
        Fake avatar=new Fake();avatar.avatarOnly=true;ArrayList<String> preferred=SingerPortraits.resolve(track("程响"),avatar);check(!preferred.get(0).contains("softhead"),"available singer portrait replaces avatar");
        check(SingerPortraits.matchAuthor(fixture("author-search-",4442),"程响响响")==null,"reject fuzzy artist mismatch");
        check(SingerPortraits.names("顾峰、顾峰，斯琴高丽").size()==2,"deduplicate singers");
        check(SingerPortraits.names("A feat. B").equals(Arrays.asList("A","B")),"feat split");
        check(SingerPortraits.nameKey(" <em>程响</em> ").equals("程响"),"strip markup and spaces");
        Fake unknown=new Fake();check(SingerPortraits.resolve(track("无法匹配的歌手"),unknown).isEmpty(),"no unrelated artist photo for no exact match");
        Fake offline=new Fake();offline.failAudio=true;offline.failSearch=true;boolean failed=false;try{SingerPortraits.resolve(track("程响"),offline);}catch(IOException e){failed=true;}check(failed,"offline is error rather than no pictures");
        check(!SingerPortraits.searchParams("程响").containsKey("token")&&!SingerPortraits.searchParams("程响").containsKey("userid"),"no account credentials in singer search");
        System.out.println("PASS: "+checks+" singer lookup checks; empty-song fallback, exact matches, duplicates, duet, errors and portrait priority");
    }
}
