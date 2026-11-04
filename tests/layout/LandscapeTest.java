package com.cloudtune.player;
import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;
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
public class LandscapeTest {
    public static class App extends MainActivity {
        public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(b);}
    }
    void measure(Activity app,int w,int h){View decor=app.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));decor.layout(0,0,w,h);}
    void capture(View view,String name)throws Exception{Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(System.getProperty("landscape.output")+"/"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    @Test public void landscapeLibraryKeepsListAndMiniPlayerVisible()throws Exception{
        for(int height:new int[]{360,280}){
            int width=height==360?800:640;RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-land-mdpi");
            ActivityController<App> controller=Robolectric.buildActivity(App.class).setup().visible();App app=controller.get();
            for(boolean local:new boolean[]{false,true}){
                app.localMode=local;app.showHome();app.tracks.clear();
                for(int i=0;i<30;i++)app.tracks.add(new Track(new JSONObject().put("hash","TEST"+i).put("name","横屏音乐 "+(i+1)).put("author_name","歌手 "+(i+1))));
                app.filter();app.count.setText("30 首音乐 · 只听自己喜欢的");app.status.setText("");measure(app,width,height);
                assertTrue("visible list height: "+height,app.list.getHeight()>70);assertTrue("list gets main width",app.list.getWidth()>width*.45f);
                Rect list=new Rect(),mini=new Rect();assertTrue(app.list.getGlobalVisibleRect(list));assertTrue(app.mini.getGlobalVisibleRect(mini));
                assertTrue("list above mini",list.bottom<=mini.top);assertEquals("mini fully visible",app.mini.getHeight(),mini.height());assertTrue("rows visible",app.list.getChildCount()>=1);
                if(height==360)capture(app.root,local?"home-local":"home-cloud");
            }
            controller.pause().stop().destroy();
        }
    }
    @Test public void landscapePlayerSplitsArtAndControls()throws Exception{
        for(int height:new int[]{360,280}){
            int width=height==360?800:640;RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-land-mdpi");
            ActivityController<App> controller=Robolectric.buildActivity(App.class).setup().visible();App app=controller.get();app.showPlayer();
            app.playerTitle.setText("横屏播放示例");app.playerArtist.setText("云听HIFI");
            Bitmap image=BitmapFactory.decodeFile(System.getProperty("landscape.photo"));if(image!=null)app.portraitView.setPortrait(image,"");
            app.setLyricLines(Lyrics.parse("[00:00.00]让喜欢的音乐一直在耳边\n[00:04.00]横屏也能自在聆听"),"");app.portraitLyrics.setPosition(1000);
            measure(app,width,height);assertEquals(android.widget.LinearLayout.HORIZONTAL,app.body.getOrientation());assertTrue(app.stage.getWidth()<app.root.getWidth()*.65f);assertTrue(app.stage.getHeight()>150);
            Rect play=new Rect();assertTrue(app.mainPlay.getGlobalVisibleRect(play));assertEquals("play visible",app.mainPlay.getHeight(),play.height());
            Rect mode=new Rect();assertTrue(app.lyricBtn.getGlobalVisibleRect(mode));assertEquals("mode visible",app.lyricBtn.getHeight(),mode.height());
            app.toggleLyrics();measure(app,width,height);assertEquals(View.VISIBLE,app.lyricView.getVisibility());app.toggleLyrics();
            if(height==360)capture(app.root,"player");controller.pause().stop().destroy();
        }
    }
    @Test public void savedPlayerPageRestoresAfterRotation(){
        RuntimeEnvironment.setQualifiers("w360dp-h780dp-port-mdpi");ActivityController<App> first=Robolectric.buildActivity(App.class).setup().visible();first.get().showPlayer();Bundle state=new Bundle();first.get().onSaveInstanceState(state);first.pause().stop().destroy();
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi");ActivityController<App> second=Robolectric.buildActivity(App.class).create(state).start().resume().visible();assertEquals("player",second.get().screen);measure(second.get(),800,360);assertEquals(android.widget.LinearLayout.HORIZONTAL,second.get().body.getOrientation());second.pause().stop().destroy();
    }
}
