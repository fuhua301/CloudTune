package com.cloudtune.player;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

public class PlaybackService extends Service {
    public static final String UPDATE="com.cloudtune.player.UPDATE";
    public static PlaybackService live;
    private PcmPlayer pcm;private boolean effectFailed=false;private String effectState="";private float duck=1f;
    private final SharedPreferences.OnSharedPreferenceChangeListener effectChanges=new SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(SharedPreferences p,String key){
        if(!"enabled".equals(key)&&!"loudness".equals(key)&&!"preset".equals(key))return;
        effectFailed=false;effectState="";if(pcm!=null)pcm.effects(p.getBoolean("enabled",false),p.getBoolean("loudness",false),p.getInt("preset",0));
        else if(p.getBoolean("enabled",false)&&Native3d.installed(PlaybackService.this)&&(playing||preparing)){save();play(Math.max(0,index));}
    }};
    private MediaPlayer player; private MediaSession session; private AudioManager audio;
    private final Handler ui=new Handler();private final ExecutorService net=Executors.newSingleThreadExecutor();
    private ArrayList<Track> queue=new ArrayList<Track>();private int index=-1;private volatile int generation=0;private boolean preparing=false,playing=false,resumeFocus=false;
    private volatile boolean ready=false;private String error="";private int failed=0;private boolean focusHeld=false;
    final AudioManager.OnAudioFocusChangeListener focus=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int c){
        if(c==AudioManager.AUDIOFOCUS_LOSS){resumeFocus=false;pause();}
        else if(c==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){boolean was=playing;pauseForFocus();resumeFocus=was;}
        else if(c==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){duck=.2f;applyVolume();}
        else if(c==AudioManager.AUDIOFOCUS_GAIN){duck=1f;applyVolume();if(resumeFocus){resumeFocus=false;resume();}}
    }};
    final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};
    public static void command(Context c,String action){command(c,action,null);}
    public static void command(Context c,String action,String hash){
        Intent i=new Intent(c,PlaybackService.class).setAction(action);if(hash!=null)i.putExtra("hash",hash);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    @Override public void onCreate(){super.onCreate();AppDefaults.initialize(this);live=this;audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        session=new MediaSession(this,"云听HIFI");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){public void onPlay(){resume();}public void onPause(){pause();}public void onSkipToNext(){next(1);}public void onSkipToPrevious(){next(-1);}public void onSeekTo(long p){seek((int)p);}public void onStop(){stopSelf();}});
        getSharedPreferences("sound_effects",0).registerOnSharedPreferenceChangeListener(effectChanges);
        session.setActive(true);registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel("music","音乐播放",NotificationManager.IMPORTANCE_LOW);ch.setShowBadge(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}
        startForeground(7,notification());
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null){stopSelf();return START_NOT_STICKY;}
        String a=i.getAction();if(!new AccountStore(this).loggedIn()&&!LocalMusic.read(this).isEmpty())getSharedPreferences("player",0).edit().putString("queue_source","local").apply();
        if("STOP".equals(a)){stopSelf();return START_NOT_STICKY;}
        if("LOAD".equals(a)){reload();push();}
        else if("PLAY".equals(a)){String h=i.getStringExtra("hash");getSharedPreferences("player",0).edit().putString("queue_source",h!=null&&h.startsWith("local:")?"local":"cloud").apply();reload();int n=find(h);if(n<0)n=0;failed=0;play(n);}
        else if("AUTO".equals(a)){if(!playing&&!preparing){reload();failed=0;play(Math.max(0,index));}}
        else if("NEXT".equals(a))next(1);
        else if("PREV".equals(a))next(-1);
        else if("TOGGLE".equals(a)){if(playing||preparing)pause();else resume();}
        else if("PAUSE".equals(a))pause();
        
        return START_NOT_STICKY;
    }
    private int find(String h){if(h!=null)for(int i=0;i<queue.size();i++)if(h.equalsIgnoreCase(queue.get(i).hash))return i;return -1;}
    private void reload(){Track old=current();String h=old==null?getSharedPreferences("player",0).getString("hash",""):old.hash;queue="local".equals(getSharedPreferences("player",0).getString("queue_source","cloud"))?LocalMusic.read(this):MusicLibrary.read(this);index=find(h);if(index<0&&old!=null&&old.isLocal()=="local".equals(getSharedPreferences("player",0).getString("queue_source","cloud"))&&(player!=null||pcm!=null)){queue.add(old);index=queue.size()-1;}}
    public Track current(){return index>=0&&index<queue.size()?queue.get(index):null;}
    public boolean isPlaying(){return playing;}
    public boolean isPreparing(){return preparing;}
    public String error(){return error;}
    public int position(){if(pcm!=null)return pcm.position();try{return player==null||!ready?0:player.getCurrentPosition();}catch(Exception e){return 0;}}
    public int duration(){if(pcm!=null)return pcm.duration();try{return player==null||!ready?0:player.getDuration();}catch(Exception e){return 0;}}
    private boolean gainFocus(){if(focusHeld)return true;int f=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);focusHeld=f==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return focusHeld;}
    private void releasePlayer(){ready=false;if(pcm!=null){pcm.close();pcm=null;}if(player!=null){player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);player.release();player=null;}}
    private void play(int n){
        if(queue.isEmpty()){error="暂无歌曲，请先导入本地音乐或刷新云盘";preparing=false;playing=false;push();stopSelf();return;}
        if(!gainFocus()){error="其他应用正在使用音频，请稍后播放";push();return;}
        generation++;final int ticket=generation;releasePlayer();index=(n%queue.size()+queue.size())%queue.size();preparing=true;playing=false;error="";push();
        final Track t=current();net.execute(new Runnable(){public void run(){
            try{if(ticket!=generation)return;final String url=t.isLocal()?t.localPath():new KugouApi(PlaybackService.this).playUrl(t);ui.post(new Runnable(){public void run(){if(ticket!=generation)return;prepare(url,t,ticket);}});}
            catch(final Exception e){ui.post(new Runnable(){public void run(){if(ticket==generation)failure(e.getMessage());}});}
        }});
    }
    public String effectStatus(){
        if(!effectState.isEmpty())return effectState;
        if(!getSharedPreferences("sound_effects",0).getBoolean("enabled",false))return "音效已关闭 · 原声播放";
        if(!Native3d.installed(this))return "内置音效文件不可用，请重新安装完整版本";
        return pcm!=null&&ready?EffectPresets.name(getSharedPreferences("sound_effects",0).getInt("preset",0))+(playing?"正在处理音频":"已就绪 · 暂停中"):"播放后自动应用音效";
    }
    private void applyVolume(){if(player!=null)try{player.setVolume(duck,duck);}catch(RuntimeException ignored){}if(pcm!=null)pcm.setVolume(duck);}
    private void prepare(final String url,final Track t,final int ticket){
        SharedPreferences fx=getSharedPreferences("sound_effects",0);
        if(fx.getBoolean("enabled",false)&&Native3d.installed(this)&&!effectFailed){
            int saved=getSharedPreferences("player",0).getString("hash","").equals(t.hash)?getSharedPreferences("player",0).getInt("position",0):0;
            final PcmPlayer engine=new PcmPlayer(this,url,saved,fx.getBoolean("loudness",false),new PcmPlayer.Listener(){
                public void prepared(final PcmPlayer p){ui.post(new Runnable(){public void run(){if(ticket!=generation||pcm!=p)return;ready=true;preparing=false;playing=true;failed=0;applyVolume();save();push();ui.removeCallbacks(checkpoint);ui.postDelayed(checkpoint,5000);}});}
                public void completed(final PcmPlayer p){ui.post(new Runnable(){public void run(){if(ticket!=generation||pcm!=p)return;getSharedPreferences("player",0).edit().putInt("position",0).apply();if("one".equals(getSharedPreferences("settings",0).getString("mode","order")))play(index);else next(1);}});}
                public void failed(final PcmPlayer p,final String reason){ui.post(new Runnable(){public void run(){if(ticket!=generation||pcm!=p)return;int pos=p.position();if(pos>0)getSharedPreferences("player",0).edit().putInt("position",pos).apply();releasePlayer();effectFailed=true;effectState="音效不可用，已回退原声："+reason;preparing=true;playing=false;preparePlain(url,t,ticket);}});}
            });pcm=engine;engine.effects(fx.getBoolean("enabled",false),fx.getBoolean("loudness",false),fx.getInt("preset",0));engine.start();
            ui.postDelayed(new Runnable(){public void run(){if(ticket==generation&&preparing&&!ready&&pcm==engine){releasePlayer();effectFailed=true;effectState="音效解码连接超时，已回退原声";preparing=true;preparePlain(url,t,ticket);}}},30000);return;
        }
        preparePlain(url,t,ticket);
    }
    private void preparePlain(String url,final Track t,final int ticket){
        try{
            ready=false;final MediaPlayer p=new MediaPlayer();player=p;p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer mp){if(ticket!=generation)return;ready=true;
                int saved=getSharedPreferences("player",0).getString("hash","").equals(t.hash)?getSharedPreferences("player",0).getInt("position",0):0;
                if(saved>0&&saved<mp.getDuration()-3000)mp.seekTo(saved);
                mp.setVolume(duck,duck);mp.start();preparing=false;playing=true;failed=0;save();push();ui.removeCallbacks(checkpoint);ui.postDelayed(checkpoint,5000);
            }});
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer mp){getSharedPreferences("player",0).edit().putInt("position",0).apply();String mode=getSharedPreferences("settings",0).getString("mode","order");if("one".equals(mode))play(index);else next(1);}});
            p.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer mp,int what,int extra){if(ticket!=generation)return true;if(!ready&&what==-38)return true;failure("播放失败（"+what+"/"+extra+"），请检查网络或音频格式");return true;}});
            p.setDataSource(url);p.prepareAsync();
            ui.postDelayed(new Runnable(){public void run(){if(ticket==generation&&preparing&&!ready)failure("连接超时，请重试");}},30000);
        }catch(Exception e){failure("无法播放："+e.getMessage());}
    }
    // Stop after three consecutive unavailable files rather than hammering the API.
    private void failure(String msg){releasePlayer();playing=false;preparing=false;error=msg==null?"网络请求失败":msg;failed++;push();if(failed<3&&failed<queue.size()){
        final int ticket=generation;ui.postDelayed(new Runnable(){public void run(){if(ticket==generation)nextInternal(1);}},1800);
    }else{if(focusHeld){audio.abandonAudioFocus(focus);focusHeld=false;}}}
    public void next(int delta){failed=0;nextInternal(delta);}
    private void nextInternal(int delta){if(queue.isEmpty())reload();save();getSharedPreferences("player",0).edit().putInt("position",0).apply();
        String mode=getSharedPreferences("settings",0).getString("mode","order");int n=index+delta;
        if("shuffle".equals(mode)&&queue.size()>1){n=new Random().nextInt(queue.size()-1);if(n>=index)n++;}
        play(n);
    }
    public void resume(){resumeFocus=false;
        if(player!=null&&ready&&!preparing&&!effectFailed&&getSharedPreferences("sound_effects",0).getBoolean("enabled",false)&&Native3d.installed(this)){save();play(Math.max(0,index));return;}
        if(pcm!=null&&ready&&!preparing){if(!gainFocus())return;pcm.resume();playing=true;error="";push();ui.removeCallbacks(checkpoint);ui.postDelayed(checkpoint,5000);return;}
        if(player!=null&&ready&&!preparing){if(!gainFocus())return;try{player.start();playing=true;error="";push();ui.removeCallbacks(checkpoint);ui.postDelayed(checkpoint,5000);return;}catch(Exception ignored){}}
        reload();failed=0;play(Math.max(0,index));
    }
    private void pauseForFocus(){boolean held=focusHeld;focusHeld=false;pause();focusHeld=held;}
    public void pause(){resumeFocus=false;if(!ready||preparing)generation++;if(preparing){releasePlayer();preparing=false;}
        if(pcm!=null)pcm.pause();try{if(player!=null)player.pause();}catch(Exception ignored){}playing=false;save();if(focusHeld){audio.abandonAudioFocus(focus);focusHeld=false;}push();}
    public void seek(int ms){if(pcm!=null&&ready&&!preparing){pcm.seek(ms);return;}if(player!=null&&ready&&!preparing)try{player.seekTo(Math.max(0,Math.min(ms,duration())));save();push();}catch(Exception ignored){}}
    final Runnable checkpoint=new Runnable(){public void run(){if(playing){save();ui.postDelayed(this,5000);}}};
    private void save(){Track t=current();if(t!=null)getSharedPreferences("player",0).edit().putString("hash",t.hash).putInt("position",position()).putString("title",t.name).putString("artist",t.artist).apply();}
    private void push(){
        Track t=current();String title=t==null?"云听HIFI · 酷狗云盘":t.name;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,t==null?"":t.artist).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP)
            .setState(preparing?PlaybackState.STATE_BUFFERING:playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position(),playing?1:0).build());
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification());PlayerWidget.updateAll(this);sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));
    }
    int drawable(String name){return getResources().getIdentifier(name,"drawable",getPackageName());}
    private PendingIntent action(String name,int req){return PendingIntent.getService(this,req,new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){
        Track t=current();PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"music"):new Notification.Builder(this);
        b.setSmallIcon(drawable("ic_note")).setContentTitle(t==null?"云听HIFI":t.name).setContentText(!error.isEmpty()?error:preparing?"正在连接云盘…":t==null?"本地与云盘，随时畅听":t.artist).setContentIntent(open).setOngoing(playing||preparing).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
        .addAction(new Notification.Action.Builder(drawable("ic_prev"),"上一首",action("PREV",1)).build())
        .addAction(new Notification.Action.Builder(drawable(playing||preparing?"ic_pause":"ic_play"),playing||preparing?"暂停":"播放",action("TOGGLE",2)).build())
        .addAction(new Notification.Action.Builder(drawable("ic_next"),"下一首",action("NEXT",3)).build())
        .addAction(new Notification.Action.Builder(drawable("ic_close"),"关闭",action("STOP",4)).build())
        .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2));return b.build();
    }
    @Override public void onDestroy(){getSharedPreferences("sound_effects",0).unregisterOnSharedPreferenceChangeListener(effectChanges);generation++;ui.removeCallbacksAndMessages(null);net.shutdownNow();save();releasePlayer();if(focusHeld)audio.abandonAudioFocus(focus);unregisterReceiver(noisy);session.release();live=null;PlayerWidget.updateAll(this);sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
