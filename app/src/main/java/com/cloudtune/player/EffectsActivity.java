package com.cloudtune.player;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Built-in original DSP and impulse presets. The master switch is the sole bypass. */
public class EffectsActivity extends Activity {
    private static final int BG=0xff121315,CARD=0xff222326,GOLD=0xffe8bf85,WHITE=0xfff3efe8,MUTED=0xffa29d95;
    private final Handler ui=new Handler();private SharedPreferences prefs;
    private TextView status,title,state;private Switch power,gain;private boolean binding,dead;
    private final LinearLayout[] cards=new LinearLayout[EffectPresets.NAMES.length];private final TextView[] actions=new TextView[EffectPresets.NAMES.length];
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void space(LinearLayout l,int h){l.addView(new View(this),lp(1,h));}
    private void tint(Switch v){v.setTextColor(GOLD);v.setThumbTintList(ColorStateList.valueOf(GOLD));v.setTrackTintList(ColorStateList.valueOf(0xff6b573e));}
    @Override public void onCreate(Bundle b){super.onCreate(b);AppDefaults.initialize(this);prefs=getSharedPreferences("sound_effects",0);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(6),dp(20),0);setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",36,WHITE);back.setGravity(Gravity.CENTER);back.setContentDescription("返回播放器");top.addView(back,lp(38,54));back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});
        TextView brand=text("蝰蛇音效",22,GOLD);brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,dp(54),1));power=new Switch(this);tint(power);power.setTextSize(12);power.setContentDescription("音效总开关，关闭后原声播放");top.addView(power);root.addView(top);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=column();body.setPadding(0,0,0,dp(28));scroll.addView(body);
        FrameLayout hero=new FrameLayout(this);hero.setBackground(bg(0xff19191b,24));hero.setMinimumHeight(dp(220));hero.addView(new Rings(this),new FrameLayout.LayoutParams(-1,-1));LinearLayout labels=column();labels.setGravity(Gravity.CENTER);labels.setPadding(dp(18),dp(12),dp(18),dp(12));hero.addView(labels,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));TextView tagline=text("让音乐，拥有空间。",13,MUTED);tagline.setGravity(Gravity.CENTER);labels.addView(tagline);space(labels,10);title=text("3D丽音",32,GOLD);title.setTypeface(null,1);title.setGravity(Gravity.CENTER);labels.addView(title);space(labels,8);state=text("内置音效 · 即点即用",13,GOLD);state.setGravity(Gravity.CENTER);state.setLineSpacing(dp(3),1);labels.addView(state);body.addView(hero,lp(-1,-2));space(body,20);
        TextView heading=text("精选音效",21,WHITE);heading.setTypeface(null,1);body.addView(heading);space(body,12);
        String[] icons={"◉","◇","LIVE","≋","♫","◎","◌","◉","HIFI","⟳"};
        // Visual order is independent of stored preset identifiers.
        int[] displayOrder={0,8,1,2,3,4,5,6,7,9};
        for(int first=0;first<displayOrder.length;first+=2){LinearLayout row=new EqualHeightRow();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.TOP);
            for(int j=0;j<2;j++){final int position=first+j;if(position>=displayOrder.length)break;final int id=displayOrder[position];if(j==1)row.addView(new View(this),lp(12,1));if(id>=cards.length){row.addView(new View(this),new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));continue;}
                LinearLayout card=column();cards[id]=card;card.setPadding(dp(14),dp(12),dp(10),dp(10));TextView icon=text(icons[id],(id==2||id==8)?20:29,GOLD);card.addView(icon,lp(-1,30));TextView name=text(EffectPresets.NAMES[id],20,WHITE);name.setTypeface(null,1);name.setMinHeight(dp(28));card.addView(name,lp(-1,-2));TextView note=text(EffectPresets.NOTES[id],10,MUTED);note.setTextSize(12);note.setGravity(Gravity.TOP);note.setLineSpacing(dp(3),1);note.setMinHeight(dp(52));card.addView(note,new LinearLayout.LayoutParams(-1,-2,1));actions[id]=text("使用",13,GOLD);card.addView(actions[id],lp(-1,24));card.setContentDescription("使用"+EffectPresets.NAMES[id]);card.setOnClickListener(new View.OnClickListener(){public void onClick(View v){select(EffectPresets.IDS[id]);}});row.addView(card,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
            }body.addView(row,new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));space(body,12);
        }
        status=text("",13,GOLD);status.setLineSpacing(dp(4),1);status.setPadding(dp(16),dp(14),dp(16),dp(14));status.setBackground(bg(CARD,16));body.addView(status,lp(-1,-2));space(body,16);
        LinearLayout gainBox=column();gainBox.setPadding(dp(16),dp(8),dp(16),dp(14));gainBox.setBackground(bg(CARD,18));gain=new Switch(this);gain.setText("动态响度增强");gain.setTextSize(16);gain.setMinHeight(dp(48));tint(gain);gainBox.addView(gain);gainBox.addView(text("可选处理，开启后音量可能变大。",12,MUTED));body.addView(gainBox);space(body,18);
        TextView tip=text("推荐佩戴耳机体验 HIFI现场和3D旋转。",12,MUTED);tip.setLineSpacing(dp(4),1);body.addView(tip);space(body,8);
        power.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton v,boolean on){if(binding)return;if(on&&!Native3d.installed(EffectsActivity.this)){Toast.makeText(EffectsActivity.this,"内置音效未能加载，请使用 ARM64 完整安装包",Toast.LENGTH_LONG).show();refresh();return;}prefs.edit().putBoolean("enabled",on).apply();refresh();}});
        gain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton v,boolean on){if(!binding)prefs.edit().putBoolean("loudness",on).apply();}});refresh();ui.post(tick);
    }
    private void select(int id){if(!Native3d.installed(this)){Toast.makeText(this,"内置音效未能加载，请使用 ARM64 完整安装包",Toast.LENGTH_LONG).show();return;}prefs.edit().putInt("preset",id).putBoolean("enabled",true).apply();refresh();}
    private void refresh(){if(dead)return;boolean on=prefs.getBoolean("enabled",false);int selected=EffectPresets.clamp(prefs.getInt("preset",0));binding=true;power.setChecked(on);power.setText(on?"已开启 ":"已关闭 ");gain.setChecked(prefs.getBoolean("loudness",false));binding=false;
        title.setText(on?EffectPresets.name(selected):"音效已关闭");state.setText(on?EffectPresets.note(selected):"原声播放");
        for(int i=0;i<cards.length;i++){boolean active=on&&selected==EffectPresets.IDS[i];GradientDrawable g=bg(active?0xff30291f:CARD,18);if(active)g.setStroke(dp(1.5f),GOLD);cards[i].setBackground(g);actions[i].setText(active?"已选择":"使用");}
        PlaybackService s=PlaybackService.live;status.setText(!Native3d.installed(this)?"此安装包的内置音效不可用，当前保持原声":!on?"总开关已关闭 · 原声播放":s!=null?s.effectStatus():"内置引擎已就绪 · 播放后自动应用");
    }
    private final Runnable tick=new Runnable(){public void run(){if(dead)return;refresh();ui.postDelayed(this,1000);}};
    @Override protected void onDestroy(){dead=true;ui.removeCallbacksAndMessages(null);super.onDestroy();}
    /** Measure natural content first, then stretch only the shorter card in each row. */
    private final class EqualHeightRow extends LinearLayout {
        EqualHeightRow(){super(EffectsActivity.this);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            super.onMeasure(widthSpec,heightSpec);
            int height=0;
            for(int i=0;i<getChildCount();i++)height=Math.max(height,getChildAt(i).getMeasuredHeight());
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);
                if(child instanceof LinearLayout)child.measure(View.MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(getMeasuredWidth(),height);
        }
    }
    private final class Rings extends View {
        private final Paint paint=new Paint(3);private final Path path=new Path();Rings(Context c){super(c);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.7f));for(int r=0;r<22;r++){paint.setColor(Color.argb(25+r*3,232,191,133));path.reset();float rx=w*(.16f+r*.017f),ry=h*(.12f+r*.008f);for(int j=0;j<=100;j++){double a=j*Math.PI*2/100;float x=w/2+(float)Math.cos(a)*rx,y=h*.56f+(float)Math.sin(a)*ry+(float)Math.sin(a*3+r*.13)*dp(8);if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,paint);}}
    }
}
