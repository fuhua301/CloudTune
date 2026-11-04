package com.cloudtune.player;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import java.util.*;

/** Scrolling-style lyric display: current line highlighted in the centre, neighbours fading out. */
public class LyricView extends View {
    private ArrayList<Lyrics.Line> lines=null;private String hint="";private int cur=-1;private boolean twoLine;
    private final TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public LyricView(Context c){super(c);p.setTextAlign(Paint.Align.CENTER);}
    float dp(float v){return v*getResources().getDisplayMetrics().density;}
    public void setTwoLine(boolean value){twoLine=value;invalidate();}
    public void setLines(ArrayList<Lyrics.Line> l,String h){lines=l;hint=h==null?"":h;cur=-1;invalidate();}
    public void setPosition(int ms){
        if(lines==null||lines.isEmpty())return;
        int n=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).ms<=ms)n=i;else break;}
        if(n!=cur){cur=n;invalidate();}
    }
    @Override protected void onDraw(Canvas c){
        c.clipRect(0,0,getWidth(),getHeight());
        float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
        if(lines==null||lines.isEmpty()){
            p.setTextSize(dp(15));p.setColor(0xff82938a);p.setTypeface(Typeface.DEFAULT);
            c.drawText(TextUtils.ellipsize(hint,p,w-dp(40),TextUtils.TruncateAt.END).toString(),cx,cy+dp(5),p);return;
        }
        if(twoLine){
            int anchor=Math.max(cur,0);p.setTextAlign(Paint.Align.LEFT);
            for(int row=0;row<2;row++){
                int i=anchor+row;if(i>=lines.size())break;
                p.setTypeface(row==0?Typeface.create("sans-serif-medium",0):Typeface.DEFAULT);
                p.setTextSize(dp(row==0?19:16));p.setColor(row==0?0xff183e34:0xff75867a);p.setAlpha(255);
                c.drawText(TextUtils.ellipsize(lines.get(i).text,p,w-dp(36),TextUtils.TruncateAt.END).toString(),dp(18),dp(29+row*33),p);
            }
            p.setTextAlign(Paint.Align.CENTER);return;
        }
        int anchor=Math.max(cur,0);float lh=dp(40);int span=(int)(h/lh/2)+2;
        for(int i=anchor-span;i<=anchor+span;i++){
            if(i<0||i>=lines.size())continue;
            boolean active=i==cur;int dist=Math.abs(i-anchor);
            p.setTypeface(active?Typeface.create("sans-serif-medium",0):Typeface.DEFAULT);
            p.setTextSize(dp(active?19:15));
            p.setColor(active?0xff236d59:0xff82938a);p.setAlpha(active?255:Math.max(50,200-dist*45));
            String s=TextUtils.ellipsize(lines.get(i).text,p,w-dp(36),TextUtils.TruncateAt.END).toString();
            c.drawText(s,cx,cy+(i-anchor)*lh+dp(6),p);
        }
    }
}
