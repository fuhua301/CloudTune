package com.cloudtune.player;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Compact native record card. Every control is connected to the existing player. */
public class PlayerWidget extends AppWidgetProvider {
    static final String ACTION="com.cloudtune.player.WIDGET";
    private static Bitmap record;
    static int res(Context c,String type,String name){return c.getResources().getIdentifier(name,type,c.getPackageName());}
    public static void updateAll(Context c){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        new PlayerWidget().onUpdate(c,manager,manager.getAppWidgetIds(new ComponentName(c,PlayerWidget.class)));
    }
    static Bitmap icon(Context c,String name,int color){
        int size=Math.max(48,(int)(24*c.getResources().getDisplayMetrics().density));
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Drawable d=c.getResources().getDrawable(res(c,"drawable",name),c.getTheme()).mutate();d.setTint(color);d.setBounds(0,0,size,size);d.draw(new Canvas(b));return b;
    }
    static PendingIntent open(Context c,String page,int request){
        Intent i=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("widget_page",page);
        return PendingIntent.getActivity(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent command(Context c,String cmd,int request){
        Intent i=new Intent(c,PlayerWidget.class).setAction(ACTION).putExtra("cmd",cmd);
        return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        if(ids==null||ids.length==0)return;
        boolean light=c.getSharedPreferences("settings",0).getBoolean("widget_light",false);
        int foreground=light?0xff24473f:Color.WHITE;
        PlaybackService s=PlaybackService.live;Track t=s==null?null:s.current();boolean logged=new AccountStore(c).loggedIn()||!LocalMusic.read(c).isEmpty();
        String title=t!=null?t.name:logged?c.getSharedPreferences("player",0).getString("title","我的酷狗云盘"):"登录酷狗，听你的云盘";
        String artist=t!=null?t.artist:logged?c.getSharedPreferences("player",0).getString("artist",""):"";
        if(artist!=null&&!artist.isEmpty()&&!"我的酷狗云盘".equals(artist)&&!"我的酷狗云盘音乐".equals(artist)&&!title.contains(artist))title+=" - "+artist;
        boolean active=s!=null&&(s.isPlaying()||s.isPreparing());
        if(s!=null&&s.isPreparing())title="加载中… "+title;
        String mode=c.getSharedPreferences("settings",0).getString("mode","order");
        String modeIcon="shuffle".equals(mode)?"ic_shuffle":"one".equals(mode)?"ic_repeat_one":"ic_repeat";
        String modeName="shuffle".equals(mode)?"随机播放":"one".equals(mode)?"单曲循环":"顺序循环";
        if(record==null)record=DiscView.bitmap(180);
        for(int id:ids){
            int height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,72);
            String layout=height<52?"player_widget_tiny":height<72?"player_widget_compact":"player_widget";
            RemoteViews v=new RemoteViews(c.getPackageName(),res(c,"layout",layout));
            v.setInt(res(c,"id","widget_root"),"setBackgroundResource",res(c,"drawable",light?"widget_bg_light":"widget_bg"));
            v.setTextViewText(res(c,"id","widget_title"),title);v.setTextColor(res(c,"id","widget_title"),foreground);
            v.setImageViewBitmap(res(c,"id","widget_record"),record);
            String[] views={"widget_mode","widget_prev","widget_play","widget_next","widget_queue","widget_skin"};
            String[] icons={modeIcon,"ic_prev",active?"ic_pause":"ic_play","ic_next","ic_queue","ic_skin"};
            for(int n=0;n<views.length;n++)v.setImageViewBitmap(res(c,"id",views[n]),icon(c,icons[n],foreground));
            v.setContentDescription(res(c,"id","widget_mode"),modeName+"，点击切换");
            v.setContentDescription(res(c,"id","widget_play"),active?"暂停":"播放");
            v.setContentDescription(res(c,"id","widget_skin"),light?"切换为深色卡片":"切换为浅色卡片");
            PendingIntent player=open(c,"player",101),library=open(c,"library",102);
            for(String name:new String[]{"widget_root","widget_record","widget_title"})v.setOnClickPendingIntent(res(c,"id",name),player);
            v.setOnClickPendingIntent(res(c,"id","widget_queue"),library);
            String[] controls={"widget_mode","widget_prev","widget_play","widget_next","widget_skin"};
            String[] actions={"MODE","PREV","TOGGLE","NEXT","THEME"};
            for(int n=0;n<controls.length;n++)v.setOnClickPendingIntent(res(c,"id",controls[n]),command(c,actions[n],120+n));
            manager.updateAppWidget(id,v);
        }
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){onUpdate(c,m,new int[]{id});}
    @Override public void onReceive(Context c,Intent i){
        super.onReceive(c,i);
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){updateAll(c);return;}
        if(!ACTION.equals(i.getAction()))return;
        String cmd=i.getStringExtra("cmd");
        if("THEME".equals(cmd)){
            android.content.SharedPreferences pref=c.getSharedPreferences("settings",0);pref.edit().putBoolean("widget_light",!pref.getBoolean("widget_light",false)).apply();updateAll(c);return;
        }
        if("MODE".equals(cmd)){cycleMode(c);updateAll(c);c.sendBroadcast(new Intent(PlaybackService.UPDATE).setPackage(c.getPackageName()));return;}
        if(!"PREV".equals(cmd)&&!"TOGGLE".equals(cmd)&&!"NEXT".equals(cmd))return;
        if((!new AccountStore(c).loggedIn()||MusicLibrary.read(c).isEmpty())&&LocalMusic.read(c).isEmpty()){
            c.startActivity(new Intent(c,MainActivity.class).putExtra("widget_page","library").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));return;
        }
        PlaybackService.command(c,cmd);
    }
    static void cycleMode(Context c){String m=c.getSharedPreferences("settings",0).getString("mode","order");String next="order".equals(m)?"shuffle":"shuffle".equals(m)?"one":"order";c.getSharedPreferences("settings",0).edit().putString("mode",next).apply();}
}
