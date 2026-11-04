package com.cloudtune.player;

import android.content.Context;
import java.io.File;

/** JNI owns one DSP instance per decoder, never a global playback hook. */
public final class Native3d {
    private static boolean available;
    static {try{System.loadLibrary("cloudtune_fx");available=true;}catch(LinkageError ignored){available=false;}}
    public static boolean supported(){return available;}
    public static File directory(Context c){return new File(c.getApplicationInfo().nativeLibraryDir);}
    public static boolean installed(Context c){File d=directory(c);return supported()&&new File(d,"libeffect0.so").isFile()&&new File(d,"libc++_shared.so").isFile()&&new File(d,"libviper4android.so").isFile();}
    public static long open(Context c,int sampleRate,boolean loudness,int preset){File d=directory(c);return create(new File(d,"libc++_shared.so").getAbsolutePath(),new File(d,"libeffect0.so").getAbsolutePath(),new File(d,"libviper4android.so").getAbsolutePath(),sampleRate,loudness,preset);}
    private static native long create(String runtime,String library,String viper,int sampleRate,boolean loudness,int preset);
    public static native void configure(long handle,boolean enabled,boolean loudness,int preset);
    public static native int process(long handle,float[] samples,int frames);
    public static native void destroy(long handle);
}
