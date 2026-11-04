package com.cloudtune.player;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Rounded translucent material over a small, CPU-blurred in-memory view snapshot. */
final class FrostedDrawable extends Drawable {
    private final Bitmap image;private final Paint paint=new Paint(3);private final float radius;
    FrostedDrawable(View view,float round){
        radius=round;int w=Math.max(1,view.getWidth()/7),h=Math.max(1,view.getHeight()/7);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale(w/(float)Math.max(1,view.getWidth()),h/(float)Math.max(1,view.getHeight()));view.draw(c);
        int[] pixels=new int[w*h],next=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);
        for(int pass=0;pass<4;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int r=0,g=0,bl=0,n=0;for(int yy=Math.max(0,y-2);yy<=Math.min(h-1,y+2);yy++)for(int xx=Math.max(0,x-2);xx<=Math.min(w-1,x+2);xx++){int v=pixels[yy*w+xx];r+=(v>>16)&255;g+=(v>>8)&255;bl+=v&255;n++;}
                next[y*w+x]=Color.rgb(r/n,g/n,bl/n);
            }int[] swap=pixels;pixels=next;next=swap;
        }b.setPixels(pixels,0,w,0,0,w,h);image=b;
    }
    @Override public void draw(Canvas canvas){Rect bounds=getBounds();RectF rect=new RectF(bounds);BitmapShader shader=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix m=new Matrix();m.setScale(bounds.width()/(float)image.getWidth(),bounds.height()/(float)image.getHeight());m.postTranslate(bounds.left,bounds.top);shader.setLocalMatrix(m);paint.setShader(shader);canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);paint.setColor(0xdaf8f9fc);canvas.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0xbaffffff);canvas.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
