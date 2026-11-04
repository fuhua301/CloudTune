package com.cloudtune.player;

import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerPortraitTest {
    public static class Player extends MainActivity {
        @Override public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(b);showPlayer();}
        @Override void showLogin(){showPlayer();}
    }
    @Test public void photoLyricsSwitchAndRender() throws Exception {
        RuntimeEnvironment.setQualifiers("w360dp-h780dp-mdpi");
        ActivityController<Player> controller=Robolectric.buildActivity(Player.class).setup().visible();Player a=controller.get();
        assertFalse(a.lyricsOn);assertEquals("歌词",a.lyricBtn.getText().toString());
        assertEquals(View.VISIBLE,a.portraitView.getVisibility());assertEquals(View.GONE,a.lyricView.getVisibility());
        ArrayList<Lyrics.Line> lines=Lyrics.parse("[00:00.00]星星坠落 风在吹动\n[00:04.00]终于再将你拥入怀中\n[00:08.00]用两颗心相伴");
        a.setLyricLines(lines,"");a.portraitLyrics.setPosition(4500);
        Field current=LyricView.class.getDeclaredField("cur");current.setAccessible(true);assertEquals(1,current.getInt(a.portraitLyrics));
        a.toggleLyrics();assertTrue(a.lyricsOn);assertEquals("写真",a.lyricBtn.getText().toString());
        assertEquals(View.GONE,a.portraitView.getVisibility());assertEquals(View.VISIBLE,a.lyricView.getVisibility());
        a.toggleLyrics();assertFalse(a.lyricsOn);
        Bitmap photo=BitmapFactory.decodeFile(System.getProperty("portrait.fixture"));assertNotNull(photo);a.portraitView.setPortrait(photo,"");
        a.playerTitle.setText("歌手写真 · 播放界面");a.playerArtist.setText("酷狗歌手写真 / 两行同步歌词");
        a.portraitLyrics.setPosition(1000);
        View decor=a.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(780,View.MeasureSpec.EXACTLY));decor.layout(0,0,360,780);
        assertEquals("photo fills screen width",360,a.portraitView.getWidth());assertEquals("no left inset",0,a.stage.getLeft());assertEquals("photo starts at top",0,a.stage.getTop());assertTrue("photo remains visible",a.stage.getHeight()>200);assertEquals(88,a.portraitLyrics.getHeight());
        Bitmap image=Bitmap.createBitmap(360,780,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("portrait.screenshot"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        a.shell("home","TEST");assertNull(a.portraitView);assertNull(a.portraitLyrics);
        controller.pause().stop().destroy();
    }
    @Test public void shortScreenKeepsTransportVisible() throws Exception {
        RuntimeEnvironment.setQualifiers("w320dp-h520dp-mdpi");
        ActivityController<Player> controller=Robolectric.buildActivity(Player.class).setup().visible();Player a=controller.get();
        View decor=a.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(520,View.MeasureSpec.EXACTLY));decor.layout(0,0,320,520);
        assertTrue("short screen photo area",a.stage.getHeight()>100);
        android.graphics.Rect visible=new android.graphics.Rect();assertTrue("mode switch visible",a.lyricBtn.getGlobalVisibleRect(visible));assertEquals(a.lyricBtn.getHeight(),visible.height());
        assertTrue("play control visible",a.mainPlay.getGlobalVisibleRect(visible));assertEquals(a.mainPlay.getHeight(),visible.height());
        controller.pause().stop().destroy();
    }
    @Test public void responsePrioritizesPortraitsAndHasNoAccountData() throws Exception {
        String fixture=new String(Files.readAllBytes(Paths.get(System.getProperty("portrait.json"))),"UTF-8");
        ArrayList<String> urls=SingerPortraits.parse(new JSONObject(fixture));assertFalse(urls.isEmpty());
        assertTrue(urls.get(0).contains("mobile_super_portrait"));
        assertFalse(urls.get(0).contains("softhead"));
        Track track=new Track(new JSONObject("{\"hash\":\"TEST\",\"filename\":\"歌手 - 歌曲\"}"));
        TreeMap<String,String> params=SingerPortraits.params(track);assertFalse(params.containsKey("token"));assertFalse(params.containsKey("userid"));
        assertTrue(SingerPortraits.normalize("http://singerimg.kugou.com/a/{size}.jpg").contains("/1080.jpg"));
        assertEquals("",SingerPortraits.normalize("https://evil.example/a.jpg"));
        assertEquals("",SingerPortraits.normalize("file:///tmp/image.jpg"));
    }
}
