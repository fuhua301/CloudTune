package com.cloudtune.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.TextView;

/** Shape-based transport icon: independent of font baselines and font padding. */
final class PlaybackButton extends TextView {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle=new Path();
    PlaybackButton(Context context){super(context);setText("▶");setFocusable(true);}
    @Override protected void onDraw(Canvas canvas){
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float size=Math.min(getWidth(),getHeight());
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        String state=getText().toString();
        if("Ⅱ".equals(state)){
            float halfHeight=size*.15f,bar=size*.065f,gap=size*.07f;
            canvas.drawRect(cx-gap/2-bar,cy-halfHeight,cx-gap/2,cy+halfHeight,paint);
            canvas.drawRect(cx+gap/2,cy-halfHeight,cx+gap/2+bar,cy+halfHeight,paint);
        }else if("■".equals(state)){
            float half=size*.11f;canvas.drawRect(cx-half,cy-half,cx+half,cy+half,paint);
        }else{
            float width=size*.24f,halfHeight=size*.15f;
            // The triangle's area centroid is exactly the circle centre.
            float left=cx-width/3f,right=cx+width*2f/3f;
            triangle.reset();triangle.moveTo(left,cy-halfHeight);triangle.lineTo(right,cy);triangle.lineTo(left,cy+halfHeight);triangle.close();
            canvas.drawPath(triangle,paint);
        }
    }
}
