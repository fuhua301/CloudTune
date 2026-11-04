package com.cloudtune.player;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=0xfff5f7f2,INK=0xff183e34,MUTED=0xff82938a,GREEN=0xff236d59,MINT=0xffd8eddf;
    final Handler handler=new Handler();final ExecutorService jobs=Executors.newSingleThreadExecutor();
    ArrayList<Track> tracks=new ArrayList<Track>(),filtered=new ArrayList<Track>();
    LinearLayout root,body,mini;TextView miniTitle,miniSub,miniPlay,playerTitle,playerArtist,elapsed,total,mainPlay,modeLabel,status,count;
    DiscView disc;SeekBar seek;ListView list;SongsAdapter adapter;ImageView qrImage;TextView qrStatus;
    String screen="",query="",qrKey="";int loginGeneration=0,loadGeneration=0;boolean loading=false,dragging=false,destroyed=false;
    KugouApi api;AccountStore account;LyricView lyricView;FrameLayout stage;TextView lyricBtn;String lyricHash="";int lyricTicket=0;boolean lyricsOn=false,lyricFailed=false;int lyricOffset=0;String lyricWait="";final ExecutorService lyricJobs=Executors.newSingleThreadExecutor();
    boolean localMode;static final int PICK_LOCAL=4101,PICK_UPLOAD=4102,PICK_FOLDER=4103;
    boolean folderScanning,folderRescan;long lastFolderScan;final java.util.concurrent.atomic.AtomicBoolean folderCancel=new java.util.concurrent.atomic.AtomicBoolean();
    final ExecutorService fileJobs=Executors.newSingleThreadExecutor();final java.util.concurrent.atomic.AtomicBoolean uploadCancel=new java.util.concurrent.atomic.AtomicBoolean();boolean uploading;ProgressDialog fileProgress;Dialog settingsPanel;
    PortraitView portraitView;LyricView portraitLyrics;String portraitHash="";int portraitTicket;
    final ExecutorService portraitJobs=Executors.newFixedThreadPool(2);Future<?> portraitTask;
    final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updatePlayback();if(adapter!=null)adapter.notifyDataSetChanged();}};
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setFontFeatureSettings("kern");return t;}
    TextView bold(String s,int size,int color){TextView t=text(s,size,color);t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    void pad(View v,int h,int w){v.setPadding(dp(h),dp(w),dp(h),dp(w));}
    TextView button(String label,boolean primary,View.OnClickListener click){TextView t=bold(label,15,primary?Color.WHITE:GREEN);t.setGravity(Gravity.CENTER);t.setBackground(bg(primary?GREEN:MINT,17));t.setMinHeight(dp(50));t.setOnClickListener(click);return t;}
    void spacer(LinearLayout l,int h){l.addView(new View(this),lp(1,h));}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        AppDefaults.initialize(this);localMode=b!=null?b.getBoolean("local_mode",false):getSharedPreferences("settings",0).getBoolean("local_mode",false);
        String restoredScreen=b!=null?b.getString("screen"):getIntent().getStringExtra("widget_page");
        api=new KugouApi(this);account=new AccountStore(this);registerReceiver(updates,new IntentFilter(PlaybackService.UPDATE));
        if(account.loggedIn()){
            tracks=localMode?LocalMusic.read(this):MusicLibrary.read(this);if("player".equals(restoredScreen))showPlayer();else showHome();if(!localMode)refreshCloud(b==null);
        }else{localMode=true;tracks=LocalMusic.read(this);if("player".equals(restoredScreen))showPlayer();else showHome();}
        PlaybackService currentService=PlaybackService.live;if(b==null&&localMode&&!tracks.isEmpty()&&getSharedPreferences("settings",0).getBoolean("autoplay",true)&&(currentService==null||(!currentService.isPlaying()&&!currentService.isPreparing()))){getSharedPreferences("player",0).edit().putString("queue_source","local").apply();PlaybackService.command(this,"AUTO");}
        handler.post(ticker);scanFolders(false);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("screen",screen);state.putBoolean("local_mode",localMode);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();if(api!=null&&System.currentTimeMillis()-lastFolderScan>3000)scanFolders(false);}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        String target=intent.getStringExtra("widget_page");if("player".equals(target))showPlayer();else if("library".equals(target))showHome();
    }
    boolean landscape(){DisplayMetrics m=getResources().getDisplayMetrics();return m.widthPixels>m.heightPixels;}
    void shell(String title,String caption){
        screen=title;portraitTicket++;if(portraitTask!=null)portraitTask.cancel(true);portraitView=null;portraitLyrics=null;portraitHash="";lyricTicket++;disc=null;seek=null;miniTitle=null;miniSub=null;miniPlay=null;mainPlay=null;playerTitle=null;playerArtist=null;elapsed=null;total=null;modeLabel=null;list=null;adapter=null;lyricView=null;stage=null;lyricBtn=null;lyricHash="";lyricWait="";lyricFailed=false;
        root=column();root.setBackgroundColor(BG);pad(root,landscape()?16:22,landscape()?4:6);setContentView(root);
        LinearLayout top=row();TextView brand=bold("云听HIFI",landscape()?20:25,INK);top.addView(brand,new LinearLayout.LayoutParams(0,dp(landscape()?40:56),1));
        TextView setting=text("☷",26,INK);setting.setGravity(Gravity.CENTER);setting.setContentDescription("设置");top.addView(setting,lp(landscape()?40:48,landscape()?40:48));setting.setOnClickListener(new View.OnClickListener(){public void onClick(View v){settings();}});root.addView(top);
        TextView cap=text(caption,10,MUTED);cap.setLetterSpacing(.18f);root.addView(cap,lp(-1,20));if(landscape())cap.setVisibility(View.GONE);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    }
    void showHome(){
        tracks=localMode?LocalMusic.read(this):MusicLibrary.read(this);query="";shell("home","YOUR MUSIC · EVERYWHERE");spacer(body,8);
        LinearLayout tabs=row();tabs.addView(button("酷狗云盘",!localMode,new View.OnClickListener(){public void onClick(View v){if(!account.loggedIn()){showLogin();return;}localMode=false;getSharedPreferences("settings",0).edit().putBoolean("local_mode",false).apply();showHome();}}),new LinearLayout.LayoutParams(0,dp(42),1));tabs.addView(new View(this),lp(8,1));tabs.addView(button("本地音乐",localMode,new View.OnClickListener(){public void onClick(View v){localMode=true;getSharedPreferences("settings",0).edit().putBoolean("local_mode",true).apply();showHome();}}),new LinearLayout.LayoutParams(0,dp(42),1));body.addView(tabs);spacer(body,10);
        LinearLayout nav=row();TextView heading=bold(localMode?"本地音乐":"我的云盘",26,INK);nav.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView refresh=button(localMode?"导入":"刷新",false,new View.OnClickListener(){public void onClick(View v){if(localMode)localImportMenu();else refreshCloud(false);}});nav.addView(refresh,lp(64,36));body.addView(nav);
        count=text(tracks.size()+" 首音乐 · 只听自己喜欢的",12,MUTED);body.addView(count,lp(-1,25));spacer(body,12);
        LinearLayout hero=row();hero.setBackground(bg(0xffdbeade,26));pad(hero,16,12);
        DiscView smallDisc=new DiscView(this);hero.addView(smallDisc,lp(localMode?92:112,localMode?100:118));LinearLayout hc=column();hero.addView(hc,new LinearLayout.LayoutParams(0,-2,1));
        hc.addView(text("A LITTLE SPACE FOR MUSIC",8,GREEN));spacer(hc,6);hc.addView(bold(localMode?"离线，也动听。":"让喜欢，\n一直在耳边。",22,INK));spacer(hc,9);
        TextView all=bold(localMode?"▶  播放本地":"▶  播放云盘",13,GREEN);all.setMinHeight(dp(42));hc.addView(all);all.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!tracks.isEmpty()){getSharedPreferences("player",0).edit().putString("queue_source",localMode?"local":"cloud").apply();PlaybackService.command(MainActivity.this,"PLAY",tracks.get(0).hash);showPlayer();}else if(localMode)localImportMenu();else refreshCloud(true);}});body.addView(hero,lp(-1,localMode?132:158));spacer(body,14);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint(localMode?"搜索本地歌曲或歌手":"搜索云盘里的歌曲或歌手");search.setBackground(bg(0xffeaf0e8,14));pad(search,15,4);body.addView(search,lp(-1,46));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int c){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
        spacer(body,8);status=text(localMode?(tracks.isEmpty()?"点击导入：选择歌曲或自动扫描文件夹":"本地副本已保存，可离线播放"):loading?"正在同步酷狗云盘…":tracks.isEmpty()?"云盘暂无音乐，点击刷新同步": "",12,MUTED);status.setMinHeight(dp(24));body.addView(status);
        list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setSelector(bg(0xffe2ecdf,12));adapter=new SongsAdapter();filter();list.setAdapter(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int pos,long id){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);PlaybackService.command(MainActivity.this,"PLAY",filtered.get(pos).hash);showPlayer();}});
        if(landscape()){
            // A narrow library panel leaves most of the width for the searchable list.
            body.removeAllViews();LinearLayout columns=row();columns.setGravity(Gravity.TOP);
            LinearLayout sidebar=column();sidebar.setPadding(dp(10),dp(6),dp(10),dp(6));sidebar.setBackground(bg(0xffeaf0e8,20));
            sidebar.addView(tabs,lp(-1,40));spacer(sidebar,4);sidebar.addView(nav,lp(-1,42));sidebar.addView(count,lp(-1,42));
            ((ViewGroup)all.getParent()).removeView(all);all.setGravity(Gravity.CENTER);all.setBackground(bg(MINT,14));sidebar.addView(all,lp(-1,40));
            int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
            columns.addView(sidebar,lp(Math.min(250,Math.max(205,(int)(width*.30f))),-1));columns.addView(new View(this),lp(12,1));
            LinearLayout content=column();content.addView(search,lp(-1,40));content.addView(status,lp(-1,22));content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            columns.addView(content,new LinearLayout.LayoutParams(0,-1,1));body.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        }
        makeMini();updatePlayback();
    }
    void filter(){filtered.clear();String q=query.toLowerCase(Locale.getDefault());for(Track t:tracks)if((t.name+" "+t.artist).toLowerCase(Locale.getDefault()).contains(q))filtered.add(t);if(adapter!=null)adapter.notifyDataSetChanged();}
    class SongsAdapter extends BaseAdapter {
        public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,android.view.ViewGroup parent){
            LinearLayout r=row();pad(r,5,7);Track t=filtered.get(pos);PlaybackService s=PlaybackService.live;boolean active=s!=null&&s.current()!=null&&t.hash.equals(s.current().hash);
            TextView number=bold(active?"♫":String.format(Locale.US,"%02d",pos+1),active?22:13,active?GREEN:MUTED);number.setGravity(Gravity.CENTER);r.addView(number,lp(38,42));
            LinearLayout labels=column();labels.setGravity(Gravity.CENTER_VERTICAL);TextView title=bold(t.name,15,active?GREEN:INK);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);labels.addView(title);TextView artist=text(t.artist,11,MUTED);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);labels.addView(artist);r.addView(labels,new LinearLayout.LayoutParams(0,dp(49),1));
            TextView dot=text("▶",14,active?GREEN:0xffb3c2b7);dot.setGravity(Gravity.CENTER);r.addView(dot,lp(32,42));return r;
        }
    }
    void makeMini(){
        mini=row();mini.setBackground(bg(INK,22));pad(mini,10,landscape()?3:6);ImageView record=new ImageView(this);record.setImageBitmap(DiscView.bitmap(120));mini.addView(record,lp(landscape()?40:48,landscape()?40:48));
        LinearLayout info=column();pad(info,10,0);miniTitle=bold("选择一首云盘音乐",14,Color.WHITE);miniTitle.setSingleLine(true);miniTitle.setEllipsize(TextUtils.TruncateAt.END);miniSub=text("云听HIFI · 随时随地",10,0xffb7d2c6);miniSub.setSingleLine(true);info.addView(miniTitle);info.addView(miniSub);mini.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showPlayer();}});
        miniPlay=text("▶",26,Color.WHITE);miniPlay.setGravity(Gravity.CENTER);miniPlay.setContentDescription("播放或暂停");mini.addView(miniPlay,lp(48,48));miniPlay.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PlaybackService.command(MainActivity.this,"TOGGLE");}});
        TextView next=text("▸▏",23,Color.WHITE);next.setGravity(Gravity.CENTER);mini.addView(next,lp(40,48));next.setContentDescription("下一首");next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PlaybackService.command(MainActivity.this,"NEXT");}});
        LinearLayout.LayoutParams p=lp(-1,landscape()?54:70);p.topMargin=dp(landscape()?4:8);p.bottomMargin=dp(landscape()?2:8);root.addView(mini,p);
    }
    void showPlayer(){
        shell("player","NOW PLAYING · CLOUD TUNE");
        root.removeViewAt(1);root.removeViewAt(0);root.setPadding(0,0,0,0);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        final boolean compact=landscape()||dm.heightPixels/dm.density<540;
        // The centre expands; the transport below it always stays at the window bottom.
        LinearLayout nav=row();TextView back=button("‹  主页",false,new View.OnClickListener(){public void onClick(View v){showHome();}});nav.addView(back,lp(85,38));TextView hint=text("留一点时间给音乐",12,MUTED);hint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);nav.addView(hint,new LinearLayout.LayoutParams(0,dp(42),1));nav.setPadding(dp(22),dp(6),dp(22),0);
        stage=new FrameLayout(this);stage.setClipChildren(true);stage.setClipToPadding(true);
        disc=new DiscView(this);disc.setVisibility(View.GONE);
        portraitView=new PortraitView(this);stage.addView(portraitView,new FrameLayout.LayoutParams(-1,-1));
        portraitLyrics=new LyricView(this);portraitLyrics.setTwoLine(true);portraitLyrics.setLines(null,"选择歌曲后显示歌词");
        FrameLayout.LayoutParams excerpt=new FrameLayout.LayoutParams(-1,dp(88),Gravity.BOTTOM);excerpt.bottomMargin=dp(8);stage.addView(portraitLyrics,excerpt);
        lyricView=new LyricView(this);setLyricLines(null,"选择一首歌曲后显示歌词");stage.addView(lyricView,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(nav,new FrameLayout.LayoutParams(-1,dp(compact?50:60),Gravity.TOP));
        body.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        lyricsOn=getSharedPreferences("settings",0).getBoolean("lyrics_on",false);
        stage.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){if(lyricsOn){lyricMenu();return true;}portraitHash="";PlaybackService sv=PlaybackService.live;ensurePortrait(sv==null?null:sv.current());return true;}});
        stage.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(lyricsOn&&lyricFailed){lyricFailed=false;lyricHash="";PlaybackService sv=PlaybackService.live;ensureLyrics(sv==null?null:sv.current());}else toggleLyrics();}});
        LinearLayout transport=column();transport.setGravity(Gravity.CENTER_HORIZONTAL);transport.setPadding(dp(22),dp(compact?2:8),dp(22),dp(compact?4:12));
        body.addView(transport,new LinearLayout.LayoutParams(-1,-2));
        playerTitle=bold("选择一首音乐",compact?20:25,INK);playerTitle.setGravity(Gravity.CENTER);playerTitle.setMaxLines(compact?1:2);playerTitle.setEllipsize(TextUtils.TruncateAt.END);transport.addView(playerTitle,lp(-1,-2));
        spacer(transport,compact?2:6);playerArtist=text("我的酷狗云盘",13,MUTED);playerArtist.setGravity(Gravity.CENTER);playerArtist.setSingleLine(true);playerArtist.setEllipsize(TextUtils.TruncateAt.END);transport.addView(playerArtist,lp(-1,compact?20:26));spacer(transport,compact?4:12);
        seek=new SeekBar(this);seek.setContentDescription("播放进度");seek.setProgressTintList(ColorStateList.valueOf(GREEN));seek.setThumbTintList(ColorStateList.valueOf(GREEN));transport.addView(seek,lp(-1,compact?30:36));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onProgressChanged(SeekBar b,int progress,boolean user){if(user&&elapsed!=null)elapsed.setText(time(progress));}public void onStopTrackingTouch(SeekBar b){dragging=false;if(PlaybackService.live!=null)PlaybackService.live.seek(b.getProgress());}});
        LinearLayout times=row();elapsed=text("0:00",11,MUTED);total=text("0:00",11,MUTED);total.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);times.addView(elapsed,new LinearLayout.LayoutParams(0,dp(18),1));times.addView(total,new LinearLayout.LayoutParams(0,dp(18),1));transport.addView(times,lp(-1,18));spacer(transport,compact?4:12);
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);
        ImageButton prev=transportIcon("ic_prev","上一首");controls.addView(prev,lp(compact?66:76,compact?62:80));prev.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PlaybackService.command(MainActivity.this,"PREV");}});
        mainPlay=new PlaybackButton(this);mainPlay.setContentDescription("播放或暂停");mainPlay.setTextColor(Color.WHITE);mainPlay.setBackground(bg(GREEN,50));controls.addView(mainPlay,lp(compact?64:82,compact?64:82));mainPlay.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PlaybackService.command(MainActivity.this,"TOGGLE");}});
        ImageButton next=transportIcon("ic_next","下一首");controls.addView(next,lp(compact?66:76,compact?62:80));next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){PlaybackService.command(MainActivity.this,"NEXT");}});transport.addView(controls,lp(-1,compact?66:86));spacer(transport,compact?4:10);
        LinearLayout bottom=row();bottom.setGravity(Gravity.CENTER);
        modeLabel=button("顺序循环",false,new View.OnClickListener(){public void onClick(View v){PlayerWidget.cycleMode(MainActivity.this);PlayerWidget.updateAll(MainActivity.this);updatePlayback();}});bottom.addView(modeLabel,new LinearLayout.LayoutParams(0,dp(compact?40:44),1.3f));bottom.addView(new View(this),lp(6,1));
        lyricBtn=button("写真",false,new View.OnClickListener(){public void onClick(View v){toggleLyrics();}});bottom.addView(lyricBtn,new LinearLayout.LayoutParams(0,dp(compact?40:44),1));bottom.addView(new View(this),lp(6,1));TextView effects=button("音效",false,new View.OnClickListener(){public void onClick(View v){startActivity(new Intent(MainActivity.this,EffectsActivity.class));}});bottom.addView(effects,new LinearLayout.LayoutParams(0,dp(compact?40:44),1));
        transport.addView(bottom,lp(-1,compact?40:48));
        if(landscape()){
            body.setOrientation(LinearLayout.HORIZONTAL);stage.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1.12f));
            body.removeView(transport);ScrollView controlsPane=new ScrollView(this);controlsPane.setFillViewport(true);controlsPane.setVerticalScrollBarEnabled(false);
            transport.setGravity(Gravity.CENTER);transport.setPadding(dp(18),dp(8),dp(18),dp(8));controlsPane.addView(transport,new ScrollView.LayoutParams(-1,-2));
            body.addView(controlsPane,new LinearLayout.LayoutParams(0,-1,1));
        }
        applyLyricMode();updatePlayback();
    }
    ImageButton transportIcon(String name,String description){
        ImageButton b=new ImageButton(this);b.setImageResource(getResources().getIdentifier(name,"drawable",getPackageName()));b.setImageTintList(ColorStateList.valueOf(GREEN));b.setBackgroundColor(Color.TRANSPARENT);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);int p=dp(22);b.setPadding(p,p,p,p);b.setContentDescription(description);return b;
    }
    TextView iconButton(String s,int size){TextView t=bold(s,size,GREEN);t.setGravity(Gravity.CENTER);return t;}
    void toggleLyrics(){lyricsOn=!lyricsOn;getSharedPreferences("settings",0).edit().putBoolean("lyrics_on",lyricsOn).apply();applyLyricMode();}
    void applyLyricMode(){
        if(disc==null||lyricView==null)return;
        lyricView.setVisibility(lyricsOn?View.VISIBLE:View.GONE);
        if(portraitView!=null)portraitView.setVisibility(lyricsOn?View.GONE:View.VISIBLE);
        if(portraitLyrics!=null)portraitLyrics.setVisibility(lyricsOn?View.GONE:View.VISIBLE);
        if(lyricBtn!=null){lyricBtn.setText(lyricsOn?"写真":"歌词");lyricBtn.setContentDescription(lyricsOn?"切换到歌手写真":"切换到完整歌词");}
        if(!lyricsOn){PlaybackService sv=PlaybackService.live;ensurePortrait(sv==null?null:sv.current());}
        handler.removeCallbacks(lyricTicker);handler.post(lyricTicker);
    }
    final Runnable lyricTicker=new Runnable(){public void run(){if(destroyed||lyricView==null)return;PlaybackService s=PlaybackService.live;if(s!=null){int pos=s.position()+300+lyricOffset;lyricView.setPosition(pos);if(portraitLyrics!=null)portraitLyrics.setPosition(pos);}handler.postDelayed(this,300);}};
    void ensureLyrics(final Track t){
        if(lyricView==null)return;
        if(t==null){if(!lyricHash.isEmpty()||!lyricWait.isEmpty()){lyricTicket++;lyricHash="";lyricWait="";setLyricLines(null,"选择一首歌曲后显示歌词");}return;}
        if(t.hash.equals(lyricHash))return;
        // Wait until the player knows the track length: it lets the lyric search pick the version with matching duration.
        PlaybackService sv=PlaybackService.live;final int dur=sv==null?0:sv.duration();
        if(dur<=0){if(!t.hash.equals(lyricWait)){lyricTicket++;lyricWait=t.hash;setLyricLines(null,"歌词加载中…");}return;}
        lyricHash=t.hash;lyricWait="";lyricFailed=false;final int ticket=++lyricTicket;setLyricLines(null,"歌词加载中…");
        lyricOffset=getSharedPreferences("lyric_offset",0).getInt(t.hash,0);
        final int pick=getSharedPreferences("lyric_pick",0).getInt(t.hash,0);
        lyricJobs.execute(new Runnable(){public void run(){
            ArrayList<Lyrics.Line> r=null;try{r=Lyrics.load(MainActivity.this,t,dur,pick);}catch(Exception e){}
            final ArrayList<Lyrics.Line> res=r;
            handler.post(new Runnable(){public void run(){
                if(destroyed||ticket!=lyricTicket||lyricView==null)return;
                if(res==null){lyricFailed=true;setLyricLines(null,"歌词加载失败，点击重试");}
                else if(res.isEmpty())setLyricLines(null,"暂无歌词");
                else setLyricLines(res,"");
            }});
        }});
    }
    void setLyricLines(ArrayList<Lyrics.Line> lines,String hint){
        if(lyricView!=null)lyricView.setLines(lines,hint);
        if(portraitLyrics!=null)portraitLyrics.setLines(lines,hint);
    }
    void ensurePortrait(final Track track){
        if(portraitView==null)return;
        if(track==null){
            if(!portraitHash.isEmpty()){portraitTicket++;if(portraitTask!=null)portraitTask.cancel(true);portraitHash="";portraitView.setPortrait(null,"选择歌曲后显示歌手写真");}
            return;
        }
        if(track.hash.equals(portraitHash))return;
        portraitHash=track.hash;final int ticket=++portraitTicket;
        if(portraitTask!=null)portraitTask.cancel(true);
        portraitView.setPortrait(null,"正在加载歌手写真…");
        portraitTask=portraitJobs.submit(new Runnable(){public void run(){
            Bitmap found=null;String message="暂未匹配到歌手图片";
            try{
                ArrayList<String> urls=SingerPortraits.urls(track);
                for(String url:urls){if(Thread.currentThread().isInterrupted())return;try{found=SingerPortraits.load(MainActivity.this,url);if(found!=null)break;}catch(Exception ignored){}}
                if(!urls.isEmpty()&&found==null)message="写真加载失败，长按重试";
            }catch(Exception e){message="写真加载失败，长按重试";}
            final Bitmap image=found;final String hint=message;
            handler.post(new Runnable(){public void run(){
                if(destroyed||ticket!=portraitTicket||portraitView==null||!track.hash.equals(portraitHash))return;
                portraitView.setPortrait(image,image==null?hint:"");
            }});
        }});
    }
    void lyricMenu(){
        if(!lyricsOn||lyricHash.isEmpty())return;
        final String h=lyricHash;
        new AlertDialog.Builder(this).setTitle("歌词校准").setItems(new String[]{"歌词提前 0.5 秒","歌词延后 0.5 秒","重置校准","换一个歌词版本"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            if(p<=2){
                lyricOffset=p==0?lyricOffset+500:p==1?lyricOffset-500:0;
                getSharedPreferences("lyric_offset",0).edit().putInt(h,lyricOffset).apply();
                toast(lyricOffset==0?"歌词校准已重置":"当前校准："+(lyricOffset>0?"提前 ":"延后 ")+Math.abs(lyricOffset)/1000f+" 秒");
            }else{
                SharedPreferences sp=getSharedPreferences("lyric_pick",0);sp.edit().putInt(h,sp.getInt(h,0)+1).apply();
                lyricHash="";PlaybackService sv=PlaybackService.live;ensureLyrics(sv==null?null:sv.current());
            }
        }}).setNegativeButton("关闭",null).show();
    }
    static String time(int ms){int s=Math.max(0,ms)/1000;return String.format(Locale.US,"%d:%02d",s/60,s%60);}
    void updatePlayback(){
        PlaybackService s=PlaybackService.live;Track t=s==null?null:s.current();boolean playing=s!=null&&s.isPlaying(),preparing=s!=null&&s.isPreparing();
        String title=t!=null?t.name:getSharedPreferences("player",0).getString("title",localMode?"选择一首本地音乐":"选择一首云盘音乐");String artist=t!=null?t.artist:localMode?"本地音乐":"我的酷狗云盘";
        String info=s!=null&&!s.error().isEmpty()?s.error():preparing?"正在连接云盘…":artist;
        if(miniTitle!=null){miniTitle.setText(title);miniSub.setText(info);miniPlay.setText(preparing?"■":playing?"Ⅱ":"▶");}
        if(playerTitle!=null){playerTitle.setText(title);playerArtist.setText(info);mainPlay.setText(preparing?"■":playing?"Ⅱ":"▶");mainPlay.setContentDescription(preparing?"取消加载":playing?"暂停":"播放");disc.spinning(playing);}
        if(seek!=null&&s!=null&&!dragging){seek.setMax(Math.max(1,s.duration()));seek.setProgress(s.position());elapsed.setText(time(s.position()));total.setText(time(s.duration()));}
        if(modeLabel!=null){String m=getSharedPreferences("settings",0).getString("mode","order");modeLabel.setText("shuffle".equals(m)?"随机播放":"one".equals(m)?"单曲循环":"顺序循环");}
        if(lyricView!=null){ensureLyrics(t);int pos=s!=null?s.position()+300+lyricOffset:0;lyricView.setPosition(pos);if(portraitLyrics!=null)portraitLyrics.setPosition(pos);if(!lyricsOn)ensurePortrait(t);}
    }
    final Runnable ticker=new Runnable(){public void run(){if(destroyed)return;updatePlayback();handler.postDelayed(this,1000);}};
    void refreshCloud(final boolean autoplay){
        if(!account.loggedIn()){showLogin();return;}
        if(loading)return;loading=true;final int ticket=++loadGeneration;if(status!=null)status.setText("正在同步酷狗云盘…");
        jobs.execute(new Runnable(){public void run(){try{
            final ArrayList<Track> result=api.cloud();
            synchronized(MainActivity.this){if(destroyed||ticket!=loadGeneration)return;MusicLibrary.save(MainActivity.this,result);}
            handler.post(new Runnable(){public void run(){if(destroyed||ticket!=loadGeneration)return;loading=false;if(!localMode)tracks=result;
                if("home".equals(screen)&&!localMode){filter();count.setText(tracks.size()+" 首音乐 · 只听自己喜欢的");status.setText(tracks.isEmpty()?"云盘暂无音乐，可先在酷狗中上传歌曲":"已同步 · "+tracks.size()+" 首云盘音乐");}
                PlayerWidget.updateAll(MainActivity.this);
                PlaybackService s=PlaybackService.live;
                if(!localMode&&autoplay&&getSharedPreferences("settings",0).getBoolean("autoplay",true)&&!tracks.isEmpty()&&(s==null||(!s.isPlaying()&&!s.isPreparing()))){getSharedPreferences("player",0).edit().putString("queue_source","cloud").apply();PlaybackService.command(MainActivity.this,"AUTO");}
                else if(s!=null)PlaybackService.command(MainActivity.this,"LOAD");
            }});
        }catch(final Exception e){handler.post(new Runnable(){public void run(){if(destroyed||ticket!=loadGeneration)return;loading=false;if(status!=null&&"home".equals(screen))status.setText("同步失败 · "+message(e));
            new AlertDialog.Builder(MainActivity.this).setTitle("暂时无法同步云盘").setMessage(message(e)+"\n\n已缓存的列表会保留。若刚刚扫码仍然出现相同错误，请截图反馈；反复登录不一定能解决。").setPositiveButton("重试",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){refreshCloud(autoplay);}}).setNeutralButton(message(e).contains("种请求方式")?"复制诊断":"重新登录",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(message(e).contains("种请求方式")){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("云听HIFI诊断",message(e)));toast("诊断已复制，不含账号或登录令牌");}else logout();}}).setNegativeButton("关闭",null).show();
        }});}}
        });
    }
    String message(Exception e){if(e instanceof java.net.UnknownHostException)return "无法连接网络，请检查网络设置";if(e instanceof java.net.SocketTimeoutException)return "连接酷狗超时，请稍后重试";String s=e.getMessage();return s==null?"请求失败，请稍后重试":s;}
    void showLogin(){
        shell("login","A HOME FOR YOUR CLOUD MUSIC");ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,-1));LinearLayout l=column();l.setGravity(Gravity.CENTER_HORIZONTAL);sc.addView(l);spacer(l,20);
        TextView heading=bold("你的云盘，\n你的专属唱片机。",29,INK);heading.setGravity(Gravity.CENTER);l.addView(heading,lp(-1,-2));spacer(l,14);
        TextView desc=text("使用酷狗音乐标准版扫码确认登录\n更新后需重新扫码，同步云盘并自动播放",13,MUTED);desc.setGravity(Gravity.CENTER);l.addView(desc,lp(-1,48));spacer(l,20);
        qrImage=new ImageView(this);qrImage.setBackground(bg(Color.WHITE,24));pad(qrImage,14,14);l.addView(qrImage,lp(238,238));
        qrStatus=text("正在生成登录二维码…",13,GREEN);qrStatus.setGravity(Gravity.CENTER);l.addView(qrStatus,lp(-1,40));
        TextView refresh=button("刷新二维码",false,new View.OnClickListener(){public void onClick(View v){startQr();}});l.addView(refresh,lp(-1,48));spacer(l,10);
        l.addView(button("打开酷狗扫一扫",true,new View.OnClickListener(){public void onClick(View v){openKugou();}}),lp(-1,50));spacer(l,16);
        TextView tip=text("同一部手机：先截下二维码 → 打开酷狗扫一扫 → 从相册选择截图 → 确认登录 → 返回云听HIFI。\n\n无需在云听HIFI输入酷狗密码。登录信息加密保存在本机。非酷狗官方客户端。",11,MUTED);tip.setGravity(Gravity.CENTER);tip.setLineSpacing(dp(3),1);l.addView(tip,lp(-1,-2));spacer(l,20);startQr();
    }
    void startQr(){
        final int ticket=++loginGeneration;qrKey="";qrImage.setImageDrawable(null);qrStatus.setText("正在生成登录二维码…");
        jobs.execute(new Runnable(){public void run(){try{final String key=api.qrKey();String url=KugouApi.qrUrl(key);
            Map<EncodeHintType,Object> hints=new EnumMap<EncodeHintType,Object>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,1);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
            BitMatrix m=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,600,600,hints);final Bitmap b=Bitmap.createBitmap(600,600,Bitmap.Config.RGB_565);int[] px=new int[600*600];for(int y=0;y<600;y++)for(int x=0;x<600;x++)px[y*600+x]=m.get(x,y)?0xff10271e:Color.WHITE;b.setPixels(px,0,600,0,0,600,600);
            handler.post(new Runnable(){public void run(){if(destroyed||ticket!=loginGeneration)return;qrKey=key;qrImage.setImageBitmap(b);qrStatus.setText("请扫码，并在酷狗中确认授权");pollQr(key,ticket,0);}});
        }catch(final Exception e){handler.post(new Runnable(){public void run(){if(!destroyed&&ticket==loginGeneration)qrStatus.setText(message(e)+"，请刷新");}});}}});
    }
    void pollQr(final String key,final int ticket,final int attempts){
        if(ticket!=loginGeneration||destroyed)return;
        handler.postDelayed(new Runnable(){public void run(){if(ticket!=loginGeneration||destroyed)return;jobs.execute(new Runnable(){public void run(){try{
            final JSONObject d=api.qrCheck(key);final int state=d.optInt("status",1);
            handler.post(new Runnable(){public void run(){if(destroyed||ticket!=loginGeneration)return;
                if(state==4){try{api.acceptLogin(d);loginGeneration++;localMode=false;getSharedPreferences("settings",0).edit().putBoolean("local_mode",false).apply();tracks.clear();showHome();refreshCloud(true);}catch(Exception e){qrStatus.setText(message(e));}return;}
                if(state==0||attempts>=90){qrStatus.setText("二维码已过期，请刷新后扫码");return;}
                qrStatus.setText(state==2?"已扫码，请在酷狗中确认登录":"等待酷狗扫码…");pollQr(key,ticket,attempts+1);
            }});
        }catch(final Exception e){handler.post(new Runnable(){public void run(){if(destroyed||ticket!=loginGeneration)return;if(attempts>=90){qrStatus.setText("连接超时，请刷新二维码");return;}qrStatus.setText("连接中断，正在重试…");pollQr(key,ticket,attempts+1);}});}}});}},2000);
    }
    void openKugou(){Intent i=getPackageManager().getLaunchIntentForPackage("com.kugou.android");if(i!=null)startActivity(i);else toast("请先安装酷狗音乐标准版，再用扫一扫识别二维码截图");}
    interface MenuAction {void click(int index);}
    void glassMenu(String heading,String[] labels,String[] details,final MenuAction action){
        final Dialog dialog=new Dialog(this);settingsPanel=dialog;
        LinearLayout panel=column();panel.setPadding(dp(16),dp(8),dp(16),dp(8));panel.setBackground(new FrostedDrawable(root,dp(30)));
        TextView title=bold(heading,23,INK);title.setGravity(Gravity.CENTER);panel.addView(title,lp(-1,56));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout options=column();scroll.addView(options);
        float screenHeight=getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density;
        int available=Math.max(52,Math.min((int)(screenHeight*.62f),(int)(screenHeight-160)));
        panel.addView(scroll,lp(-1,Math.min(labels.length*59,Math.min(438,available))));
        for(int n=0;n<labels.length;n++){
            final int index=n;LinearLayout cell=row();cell.setPadding(dp(14),0,dp(12),0);
            GradientDrawable material=bg(0x99ffffff,17);material.setStroke(dp(1),0x88ffffff);cell.setBackground(material);
            TextView label=bold(labels[n],15,INK);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);cell.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));
            String detail=details!=null&&n<details.length?details[n]:"";boolean hasDetail=detail!=null&&!detail.isEmpty();
            TextView value=text(hasDetail?detail:"›",hasDetail?12:24,MUTED);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            cell.addView(value,lp(hasDetail?Math.max(54,Math.min(100,detail.length()*12+12)):22,52));options.addView(cell,lp(-1,52));spacer(options,7);
            cell.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();action.click(index);}});
        }
        TextView close=button("完成",false,new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});panel.addView(close,lp(-1,46));dialog.setContentView(panel);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.18f;window.setAttributes(attrs);}
        dialog.show();if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),-2);
    }
    void settings(){
        final boolean auto=getSharedPreferences("settings",0).getBoolean("autoplay",true);
        String[] labels={"本地音乐", "上传本地音乐到酷狗云盘", "蝰蛇音效", "进入自动播放", "切换播放模式", "添加桌面播放卡片", account.loggedIn()?"退出当前账号":"登录酷狗账号", "关于云听HIFI"};
        String[] details={"","","",auto?"已开启":"已关闭"};
        glassMenu("设置",labels,details,new MenuAction(){public void click(int index){
            if(index==0){localMode=true;getSharedPreferences("settings",0).edit().putBoolean("local_mode",true).apply();showHome();}
            else if(index==1)pickAudio(true);
            else if(index==2)startActivity(new Intent(MainActivity.this,EffectsActivity.class));
            else if(index==3){getSharedPreferences("settings",0).edit().putBoolean("autoplay",!auto).apply();settings();}
            else if(index==4){PlayerWidget.cycleMode(MainActivity.this);PlayerWidget.updateAll(MainActivity.this);updatePlayback();toast("播放模式已切换");}
            else if(index==5)pinWidget(PlayerWidget.class);
            else if(index==6){if(account.loggedIn())new AlertDialog.Builder(MainActivity.this).setTitle("退出当前账号？").setMessage("退出后仍可播放已导入的本地音乐。").setPositiveButton("退出",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){logout();}}).setNegativeButton("取消",null).show();else showLogin();}
            else new AlertDialog.Builder(MainActivity.this).setTitle("云听HIFI 1.0.26").setMessage("本地音乐与酷狗云盘播放器。支持离线播放、上传云盘、后台播放、歌手写真与HIFI现场音效。\n\n非酷狗官方客户端，云盘操作直接连接酷狗服务。").setPositiveButton("知道了",null).show();
        }});
    }
    void localImportMenu(){
        String[] options={"选择歌曲文件","添加音乐文件夹","立即扫描已选文件夹","管理扫描文件夹"};
        String[] details={"支持多选","打开自动扫描"};
        glassMenu("导入本地音乐",options,details,new MenuAction(){public void click(int which){
            if(which==0)pickAudio(false);else if(which==1)pickFolder();else if(which==2)scanFolders(true);else manageFolders();
        }});
    }
    void pickFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_FOLDER);}catch(ActivityNotFoundException e){toast("当前系统没有可用的文件夹选择器");}
    }
    void manageFolders(){
        final ArrayList<android.net.Uri> folders=FolderScanner.folders(this);
        if(folders.isEmpty()){toast("还没有选择自动扫描文件夹");return;}
        String[] names=new String[folders.size()];for(int i=0;i<names.length;i++)names[i]=folders.get(i).getLastPathSegment();
        String[] details=new String[names.length];for(int i=0;i<details.length;i++)details[i]="移除";
        glassMenu("管理扫描文件夹",names,details,new MenuAction(){public void click(int which){
            if(folderScanning){toast("请等待当前扫描完成后再移除");return;}
            try{FolderScanner.remove(MainActivity.this,folders.get(which));toast("已停止扫描该文件夹，已导入歌曲保留");}catch(Exception e){toast("无法移除文件夹，请重试");}
        }});
    }
    void scanFolders(final boolean notify){
        if(folderScanning){if(notify){folderRescan=true;toast("正在扫描音乐文件夹");}return;}
        if(FolderScanner.folders(this).isEmpty()){if(notify)toast("请先添加音乐文件夹");return;}
        folderScanning=true;lastFolderScan=System.currentTimeMillis();folderCancel.set(false);
        if(status!=null&&localMode&&"home".equals(screen))status.setText("正在扫描音乐文件夹…");
        fileJobs.execute(new Runnable(){public void run(){
            final long[] lastUpdate={0};
            final FolderScanner.Result result=FolderScanner.scan(MainActivity.this,folderCancel,new FolderScanner.Progress(){public void update(final int checked,int imported){
                long now=System.currentTimeMillis();if(now-lastUpdate[0]<500)return;lastUpdate[0]=now;
                handler.post(new Runnable(){public void run(){if(!destroyed&&status!=null&&localMode&&"home".equals(screen))status.setText("正在扫描 · 已检查 "+checked+" 首");}});
            }});
            handler.post(new Runnable(){public void run(){
                folderScanning=false;if(destroyed)return;
                if(localMode&&"home".equals(screen)){
                    tracks=LocalMusic.read(MainActivity.this);filter();if(count!=null)count.setText(tracks.size()+" 首音乐 · 只听自己喜欢的");
                    if(status!=null)status.setText(result.failed>0?"扫描完成 · 新增 "+result.imported+" 首，部分文件或权限不可用": "扫描完成 · 新增 "+result.imported+" 首，已有歌曲已去重");
                }
                PlaybackService service=PlaybackService.live;
                if(service!=null&&service.current()!=null&&service.current().isLocal())PlaybackService.command(MainActivity.this,"LOAD");
                if(localMode&&result.imported>0&&getSharedPreferences("settings",0).getBoolean("autoplay",true)&&(service==null||(!service.isPlaying()&&!service.isPreparing()))){
                    getSharedPreferences("player",0).edit().putString("queue_source","local").apply();PlaybackService.command(MainActivity.this,"AUTO");
                }
                if(notify||result.failed>0)toast("扫描完成：新增 "+result.imported+" 首"+(result.failed>0?"，部分文件未能读取；权限失效时请重新添加文件夹":""));
                if(folderRescan){folderRescan=false;scanFolders(false);}
            }});
        }});
    }
    void pickAudio(boolean upload){
        if(upload&&!account.loggedIn()){toast("请先登录酷狗账号，再选择上传音乐");showLogin();return;}
        if(uploading){toast("已有文件任务正在进行");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(intent,upload?PICK_UPLOAD:PICK_LOCAL);}catch(ActivityNotFoundException e){toast("请先安装系统文件管理器");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_FOLDER){
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            android.net.Uri folder=data.getData();
            try{
                int granted=data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if(granted==0)throw new SecurityException();
                getContentResolver().takePersistableUriPermission(folder,granted);FolderScanner.add(this,folder);
                localMode=true;getSharedPreferences("settings",0).edit().putBoolean("local_mode",true).apply();showHome();scanFolders(true);
            }catch(Exception e){toast("未能保存文件夹访问权限，请重新选择音乐文件夹");}
            return;
        }
        if(result!=RESULT_OK||data==null||(request!=PICK_LOCAL&&request!=PICK_UPLOAD))return;
        final ArrayList<android.net.Uri> uris=new ArrayList<android.net.Uri>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;runFiles(uris,request==PICK_UPLOAD);
    }
    void runFiles(final ArrayList<android.net.Uri> uris,final boolean upload){
        if(uploading)return;uploading=true;uploadCancel.set(false);final ProgressDialog progress=new ProgressDialog(this);fileProgress=progress;progress.setTitle(upload?"上传到酷狗云盘":"导入本地音乐");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setCancelable(false);progress.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){uploadCancel.set(true);}});progress.show();
        final JSONObject session=account.read();final String user=session.optString("userid");
        fileJobs.execute(new Runnable(){public void run(){int success=0;String problem="";
            for(int n=0;n<uris.size();n++){
                if(uploadCancel.get()||Thread.currentThread().isInterrupted())break;final int number=n+1;
                try{
                    handler.post(new Runnable(){public void run(){if(!destroyed){progress.setMessage("准备文件 "+number+" / "+uris.size());progress.setProgress(0);}}});
                    Track track=LocalMusic.importUri(MainActivity.this,uris.get(n),uploadCancel);if(uploadCancel.get())break;
                    if(upload){final KugouApi uploaderApi=new KugouApi(MainActivity.this);
                        CloudUploader.Transport network=new CloudUploader.Transport(){public byte[] request(String base,String path,TreeMap<String,String> params,byte[] body,Map<String,String> headers)throws Exception{
                            if(uploadCancel.get()||!user.equals(account.read().optString("userid")))throw new java.io.InterruptedIOException("上传已取消或账号已切换");
                            return WireClient.request(base,path,params,body,null,false,uploaderApi.mid,headers);
                        }};
                        new CloudUploader(session,uploaderApi.mid,KugouApi.publicKey(),network,new CloudUploader.Progress(){public void update(final String stage,final int percent){handler.post(new Runnable(){public void run(){if(!destroyed){progress.setMessage(number+" / "+uris.size()+" · "+stage);progress.setProgress(percent);}}});}},uploadCancel).upload(track);
                    }
                    success++;
                }catch(Exception e){problem=upload?"上传未完成："+message(e):"导入失败："+message(e);break;}
            }
            final int completed=success;final String error=problem;
            handler.post(new Runnable(){public void run(){uploading=false;if(destroyed)return;progress.dismiss();toast((upload?"已上传 ":"已导入 ")+completed+" 首"+(uploadCancel.get()?" · 已取消":error.isEmpty()?"":"\n"+error));if(!upload){localMode=true;getSharedPreferences("settings",0).edit().putBoolean("local_mode",true).apply();showHome();}else if(completed>0&&account.loggedIn())refreshCloud(false);}});
        }});
    }
    void pinWidget(Class<?> cls){if(Build.VERSION.SDK_INT>=26){AppWidgetManager m=AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported()){m.requestPinAppWidget(new ComponentName(this,cls),null,null);return;}}
        new AlertDialog.Builder(this).setTitle("添加桌面卡片").setMessage("返回桌面 → 长按空白处 → 小组件 / 桌面插件 → 找到「云听HIFI · 唱片卡片」→ 拖到桌面。建议宽度 4 格，可横向拉伸。若桌面仍显示旧样式，移除旧卡片后重新添加。").setPositiveButton("知道了",null).show();}
    void logout(){
        uploadCancel.set(true);synchronized(this){loadGeneration++;loading=false;loginGeneration++;stopService(new Intent(this,PlaybackService.class));account.clear();MusicLibrary.clear(this);tracks.clear();}
        PlayerWidget.updateAll(this);localMode=true;showHome();
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if("player".equals(screen)){showHome();return;}if("login".equals(screen)){localMode=true;showHome();return;}super.onBackPressed();}
    @Override protected void onDestroy(){synchronized(this){destroyed=true;loadGeneration++;loginGeneration++;}handler.removeCallbacksAndMessages(null);if(fileProgress!=null)fileProgress.dismiss();if(settingsPanel!=null)settingsPanel.dismiss();folderCancel.set(true);uploadCancel.set(true);fileJobs.shutdownNow();jobs.shutdownNow();lyricJobs.shutdownNow();portraitTicket++;portraitJobs.shutdownNow();unregisterReceiver(updates);super.onDestroy();}
}
