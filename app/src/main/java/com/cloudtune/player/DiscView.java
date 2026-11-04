package com.cloudtune.player;
import android.view.*;
import android.content.*;
import android.graphics.*;
public class DiscView extends View {
    private final Paint p=new Paint(3);private float angle=0;private boolean spin=false;
    public DiscView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void spinning(boolean b){spin=b;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float r=Math.min(getWidth(),getHeight())*.44f;c.save();c.translate(getWidth()/2f,getHeight()/2f);c.rotate(angle);draw(c,p,r);c.restore();if(spin){angle+=.25f;postInvalidateDelayed(32);}}
    static void draw(Canvas c,Paint p,float r){
        p.setShader(null);p.setColor(Color.argb(24,18,45,42));c.drawCircle(0,r*.05f,r*1.05f,p);
        p.setShader(new SweepGradient(0,0,new int[]{0xff162e2d,0xff597573,0xff172b2b,0xff0e2221,0xff476563,0xff162e2d},null));c.drawCircle(0,0,r,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(.6f,r*.0025f));
        for(float f=.38f;f<.96;f+=.025f){p.setColor(Color.argb(26,218,237,229));c.drawCircle(0,0,r*f,p);}p.setStyle(Paint.Style.FILL);
        p.setColor(0xffbfe9d6);c.drawCircle(0,0,r*.32f,p);p.setColor(0xff236152);c.drawCircle(0,0,r*.15f,p);p.setColor(0xff0e2925);c.drawCircle(0,0,r*.045f,p);
        p.setColor(0xff317362);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(r*.07f);c.drawText("C L O U D",0,-r*.21f,p);c.drawText("T U N E",0,r*.25f,p);
    }
    public static Bitmap bitmap(int size){Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.translate(size/2f,size/2f);draw(c,new Paint(3),size*.47f);return b;}
}
