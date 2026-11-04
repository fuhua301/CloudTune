package com.cloudtune.player;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},manifest=Config.NONE,shadows=LocalSettingsTest.Metadata.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LocalSettingsTest {
    public static class App extends MainActivity {
        @Override public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(b);}
        @Override void showLogin(){screen="login";}
    }
    @Implements(MediaMetadataRetriever.class)
    public static class Metadata {
        @Implementation protected void setDataSource(String path){}
        @Implementation protected String extractMetadata(int key){return key==MediaMetadataRetriever.METADATA_KEY_TITLE?"本地测试音乐":key==MediaMetadataRetriever.METADATA_KEY_ARTIST?"测试歌手":key==MediaMetadataRetriever.METADATA_KEY_DURATION?"1000":key==MediaMetadataRetriever.METADATA_KEY_BITRATE?"128000":null;}
        @Implementation protected void release(){}
    }
    void layout(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);}
    @Test public void offlineImportAndDefaults()throws Exception{
        ActivityController<App> controller=Robolectric.buildActivity(App.class).setup().visible();App app=controller.get();assertEquals("home",app.screen);assertTrue(app.localMode);
        app.getSharedPreferences("sound_effects",0).edit().clear().commit();AppDefaults.initialize(app);assertEquals(9,app.getSharedPreferences("sound_effects",0).getInt("preset",-1));assertTrue(app.getSharedPreferences("sound_effects",0).getBoolean("enabled",false));
        app.getSharedPreferences("sound_effects",0).edit().putBoolean("enabled",false).commit();AppDefaults.initialize(app);assertFalse(app.getSharedPreferences("sound_effects",0).getBoolean("enabled",true));
        File original=new File(app.getCacheDir(),"test-local.mp3");byte[] bytes="test fixture bytes; media tags supplied by shadow".getBytes("UTF-8");Files.write(original.toPath(),bytes);
        Track track=LocalMusic.importUri(app,Uri.fromFile(original));assertTrue(track.isLocal());assertEquals("本地测试音乐",track.name);assertEquals("测试歌手",track.artist);assertEquals(KugouApi.hex(MessageDigest.getInstance("MD5").digest(bytes)),track.source.getString("local_md5"));
        assertArrayEquals(bytes,Files.readAllBytes(Paths.get(track.localPath())));LocalMusic.importUri(app,Uri.fromFile(original));assertEquals(1,LocalMusic.read(app).size());original.delete();assertEquals(1,LocalMusic.read(app).size());
        File other=new File(app.getCacheDir(),"cancel.mp3");Files.write(other.toPath(),new byte[]{1,2,3});boolean canceled=false;try{LocalMusic.importUri(app,Uri.fromFile(other),new AtomicBoolean(true));}catch(InterruptedIOException e){canceled=true;}assertTrue(canceled);assertEquals(1,LocalMusic.read(app).size());
        app.showHome();assertEquals(1,app.tracks.size());assertEquals(track.hash,app.filtered.get(0).hash);
        app.pickAudio(false);Intent picker=((ShadowActivity)org.robolectric.shadow.api.Shadow.extract(app)).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());assertEquals("audio/*",picker.getType());assertTrue(picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
        app.pickAudio(true);assertEquals("login",app.screen);
        controller.pause().stop().destroy();
    }
    TextView find(View view,String text){if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    @Test public void glassMenuRendersAndShowsUpload()throws Exception{
        RuntimeEnvironment.setQualifiers("w360dp-h780dp-mdpi");ActivityController<App> controller=Robolectric.buildActivity(App.class).setup().visible();App app=controller.get();View decor=app.getWindow().getDecorView();layout(decor,360,780);
        Bitmap home=Bitmap.createBitmap(360,780,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(home));try(FileOutputStream out=new FileOutputStream(System.getProperty("settings.screenshot")+"-home.png")){home.compress(Bitmap.CompressFormat.PNG,100,out);}
        app.settings();Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());View panel=((ViewGroup)dialog.findViewById(android.R.id.content)).getChildAt(0);assertTrue(panel.getBackground() instanceof FrostedDrawable);assertNotNull(find(panel,"上传本地音乐到酷狗云盘"));assertNotNull(find(panel,"本地音乐"));
        layout(panel,336,Math.min(680,app.dp(550)));Bitmap bitmap=Bitmap.createBitmap(panel.getWidth(),panel.getHeight(),Bitmap.Config.ARGB_8888);panel.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(System.getProperty("settings.screenshot"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}dialog.dismiss();controller.pause().stop().destroy();
    }
}
