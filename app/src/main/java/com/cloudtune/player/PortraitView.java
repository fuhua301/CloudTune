package com.cloudtune.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Large, top-biased portrait, fading into the player surface behind the lyrics. */
public final class PortraitView extends View {
    private Bitmap image;
    private String hint="选择歌曲后显示歌手写真";
    private final Paint paint=new Paint(3);
    public PortraitView(Context c){super(c);setContentDescription("歌手写真");}
    public void setPortrait(Bitmap b,String message){image=b;hint=message==null?"":message;invalidate();}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        if(w<=0||h<=0)return;
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xffd3e4d8,0xfff5f7f2},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);paint.setShader(null);
        if(image!=null&&!image.isRecycled()){
            float scale=Math.max(w/image.getWidth(),h/image.getHeight());
            float iw=image.getWidth()*scale,ih=image.getHeight()*scale;
            c.drawBitmap(image,null,new RectF((w-iw)/2,(h-ih)*.25f,(w+iw)/2,(h-ih)*.25f+ih),paint);
        }
        // Fade the photo behind the top navigation as well as the lower lyric area.
        paint.setShader(new LinearGradient(0,0,0,Math.min(h*.32f,135*d),new int[]{0xfff5f7f2,0xaaf5f7f2,0x00f5f7f2},new float[]{0,.35f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setShader(new LinearGradient(0,Math.max(0,h-190*d),0,h,new int[]{0x00f5f7f2,0xeef5f7f2,0xfff5f7f2},new float[]{0,.62f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);paint.setShader(null);
        if(!hint.isEmpty()){
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(14*d);paint.setColor(0xff527663);
            c.drawText(hint,w/2,Math.max(30*d,(h-100*d)/2),paint);
        }
    }
}
