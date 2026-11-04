package com.cloudtune.player;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exercise the real Android view hierarchy, including ScrollView's unbounded measurement. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35}, manifest=Config.NONE)
public class EffectsLayoutTest {
    private void measure(int width,float scale) throws Exception {
        RuntimeEnvironment.setQualifiers("w"+width+"dp-h800dp-mdpi");
        android.content.res.Resources resources=RuntimeEnvironment.getApplication().getResources();
        Configuration config=new Configuration(resources.getConfiguration());config.fontScale=scale;
        DisplayMetrics metrics=new DisplayMetrics();metrics.setTo(resources.getDisplayMetrics());metrics.scaledDensity=metrics.density*scale;
        resources.updateConfiguration(config,metrics);
        ActivityController<EffectsActivity> controller=Robolectric.buildActivity(EffectsActivity.class);
        controller.get().getApplicationInfo().nativeLibraryDir=RuntimeEnvironment.getApplication().getFilesDir().getAbsolutePath();
        controller.setup().visible();
        Activity activity=controller.get();assertEquals("requested font scale",scale,activity.getResources().getConfiguration().fontScale,.001f);View decor=activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,800);
        Field field=EffectsActivity.class.getDeclaredField("cards");field.setAccessible(true);LinearLayout[] cards=(LinearLayout[])field.get(activity);
        assertEquals(10,cards.length);
        for(int i=0;i<cards.length;i++){
            LinearLayout card=cards[i];String tag="width="+width+" font="+scale+" card="+i;
            assertTrue(tag+" has width",card.getWidth()>90);
            assertTrue(tag+" has content height",card.getHeight()>=140);
            assertTrue(tag+" stays in row",card.getBottom()<=((View)card.getParent()).getHeight());
            TextView name=(TextView)card.getChildAt(1),note=(TextView)card.getChildAt(2),action=(TextView)card.getChildAt(3);
            assertEquals(EffectPresets.NAMES[i],name.getText().toString());
            assertEquals(EffectPresets.NOTES[i],note.getText().toString());
            assertTrue(tag+" name visible",name.getHeight()>0);assertTrue(tag+" description visible",note.getHeight()>0);
            assertTrue(tag+" action visible",action.getHeight()>0&&action.getBottom()<=card.getHeight());
            assertTrue(tag+" complete description",note.getLayout()!=null && note.getHeight()-note.getCompoundPaddingTop()-note.getCompoundPaddingBottom()>=note.getLayout().getHeight());
        }
        int[] order={0,8,1,2,3,4,5,6,7,9};
        assertSame("HIFI beside 3D",cards[0].getParent(),cards[8].getParent());
        assertTrue("HIFI to right",cards[8].getLeft()>cards[0].getLeft());
        for(int n=0;n<order.length;n+=2){
            LinearLayout left=cards[order[n]],right=cards[order[n+1]];
            assertSame("same row",left.getParent(),right.getParent());
            assertEquals("equal row heights",left.getHeight(),right.getHeight());
            if(n>0)assertTrue("row order",((View)left.getParent()).getTop()>((View)cards[order[n-2]].getParent()).getTop());
        }
        if(width==360&&scale==1f)assertTrue("compact cards",cards[1].getHeight()<190);
        System.out.println("PASS: 10 cards measured at "+width+"dp, font scale "+scale+"; first height="+cards[0].getHeight());
        controller.pause().stop().destroy();
    }
    @Test public void narrowScreen() throws Exception {measure(320,1f);}
    @Test public void normalScreen() throws Exception {measure(360,1f);}
    @Test public void largeText() throws Exception {measure(360,1.6f);}
    @Test public void wideScreen() throws Exception {measure(480,1f);}
}
