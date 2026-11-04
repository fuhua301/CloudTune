package com.cloudtune.player;

import android.content.Context;
import android.media.*;
import android.os.PowerManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/** Streaming decoder used only after the user enables the built-in DSP engine. */
final class PcmPlayer implements Runnable {
    interface Listener {void prepared(PcmPlayer p);void completed(PcmPlayer p);void failed(PcmPlayer p,String reason);}
    private final Context context;private final String url;private final Listener listener;private final Thread thread;
    private final Object lock=new Object();private final PowerManager.WakeLock wake;
    private volatile boolean stopped,paused,effects=true,loudness;private volatile float volume=1f;private final AtomicInteger seekRequest=new AtomicInteger(-1);
    private volatile int length,rate;private volatile long baseUs;private volatile boolean anchored;private AudioTrack track;
    private long fx,written;private boolean configuredEffects=true,configuredLoudness,notified;
    private volatile int preset;private int configuredPreset=-1;private PartitionedConvolver convolver;private int tailRemaining=-1;
    private final float[] floats=new float[4096];private final short[] shorts=new short[4096];
    PcmPlayer(Context c,String source,int start,boolean gain,Listener l){context=c.getApplicationContext();url=source;listener=l;loudness=gain;seekRequest.set(start);preset=EffectPresets.clamp(c.getSharedPreferences("sound_effects",0).getInt("preset",0));
        wake=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CloudTune:PCM");wake.setReferenceCounted(false);thread=new Thread(this,"CloudTune-PCM");}
    void start(){thread.start();}
    void pause(){synchronized(lock){paused=true;if(track!=null)try{track.pause();}catch(RuntimeException ignored){}if(wake.isHeld())wake.release();}}
    void resume(){synchronized(lock){if(stopped)return;paused=false;if(track!=null)track.play();if(!wake.isHeld())wake.acquire();lock.notifyAll();}}
    void seek(int ms){seekRequest.set(Math.max(0,Math.min(ms,length)));synchronized(lock){lock.notifyAll();}}
    void effects(boolean on,boolean gain,int mode){effects=on;loudness=gain;preset=EffectPresets.clamp(mode);}
    void setVolume(float value){synchronized(lock){volume=value;if(track!=null)track.setVolume(value);}}
    void close(){synchronized(lock){stopped=true;if(track!=null)try{track.pause();track.flush();}catch(RuntimeException ignored){}if(wake.isHeld())wake.release();lock.notifyAll();}thread.interrupt();}
    int duration(){return length;}
    int position(){synchronized(lock){if(!anchored||track==null||rate<=0)return Math.max(0,seekRequest.get());try{return Math.min(length,(int)(baseUs/1000+(track.getPlaybackHeadPosition()&0xffffffffL)*1000/rate));}catch(RuntimeException e){return 0;}}}
    private boolean waitActive()throws InterruptedException{synchronized(lock){while(paused&&!stopped)lock.wait();return !stopped;}}
    private void outputFormat(MediaFormat f)throws Exception{
        int sr=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);if(sr<8000||sr>192000)throw new Exception("不支持此采样率");
        synchronized(lock){if(stopped)return;if(track!=null){track.pause();track.flush();track.release();}if(fx!=0){Native3d.destroy(fx);fx=0;}
            rate=sr;fx=Native3d.open(context,sr,loudness,preset);if(fx==0)throw new Exception("原版音效未能初始化");
            configuredEffects=effects;configuredLoudness=loudness;configuredPreset=preset;convolver=null;tailRemaining=-1;Native3d.configure(fx,effects,loudness,preset);
            int min=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw new Exception("设备音频输出不可用");
            track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),new AudioFormat.Builder().setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),Math.max(min*2,16384),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new Exception("无法创建音频输出");
            written=0;anchored=false;track.setVolume(volume);if(!paused){track.play();if(!wake.isHeld())wake.acquire();}}
        if(!notified&&!stopped){notified=true;listener.prepared(this);}
    }
    private void writeBlock(int frames)throws Exception{
        if(effects!=configuredEffects||loudness!=configuredLoudness||preset!=configuredPreset){Native3d.configure(fx,effects,loudness,preset);configuredEffects=effects;configuredLoudness=loudness;configuredPreset=preset;convolver=null;tailRemaining=-1;}
        if(effects&&configuredPreset==1){if(convolver==null){try(java.io.InputStream impulse=context.getAssets().open("soundeffect/viper_blackdiamond_headset.irs")){convolver=new PartitionedConvolver(impulse,rate);}}convolver.process(floats,frames);}
        if(effects){int n=Native3d.process(fx,floats,frames);if(n<0||n>2048)throw new Exception("原版音效返回的帧数不匹配");frames=n;}
        for(int i=0;i<frames*2;i++){float v=floats[i];if(Float.isNaN(v)||Float.isInfinite(v))throw new Exception("原版音效返回无效音频");shorts[i]=(short)Math.round(Math.max(-1f,Math.min(1f,v))*32767f);}
        int offset=0;
        while(offset<frames*2&&!stopped){if(!waitActive())return;if(seekRequest.get()>=0)return;int n;
            synchronized(lock){if(stopped)return;if(paused)continue;n=track.write(shorts,offset,frames*2-offset,AudioTrack.WRITE_NON_BLOCKING);}
            if(n<0)throw new Exception("音频输出失败："+n);if(n==0){Thread.sleep(5);continue;}offset+=n;written+=n/2;
        }
    }
    @Override public void run(){MediaExtractor extractor=null;MediaCodec codec=null;boolean success=false;
        try{
            extractor=new MediaExtractor();extractor.setDataSource(url);if(stopped)return;MediaFormat input=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){extractor.selectTrack(i);input=f;break;}}
            if(input==null)throw new Exception("找不到可解码的音轨");length=input.containsKey(MediaFormat.KEY_DURATION)?(int)Math.min(Integer.MAX_VALUE,input.getLong(MediaFormat.KEY_DURATION)/1000):0;
            int initialSeek=seekRequest.getAndSet(-1);long trimUs=initialSeek>0?initialSeek*1000L:-1;
            if(trimUs>0)extractor.seekTo(trimUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            String mime=input.getString(MediaFormat.KEY_MIME);codec=MediaCodec.createDecoderByType(mime);input.setInteger("pcm-encoding",AudioFormat.ENCODING_PCM_16BIT);codec.configure(input,null,null,0);codec.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();MediaFormat format=codec.getOutputFormat();boolean inputEnd=false,outputEnd=false;
            while(!stopped){
                if(!waitActive())break;
                int request=seekRequest.getAndSet(-1);
                if(request>=0){trimUs=request*1000L;extractor.seekTo(trimUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);codec.flush();inputEnd=false;outputEnd=false;
                    synchronized(lock){if(track!=null){track.pause();track.flush();if(!paused)track.play();}written=0;anchored=false;baseUs=trimUs;}if(fx!=0)Native3d.configure(fx,effects,loudness,preset);convolver=null;tailRemaining=-1;}
                if(outputEnd){
                    int tailSize=effects?(preset==1&&convolver!=null?convolver.tailFrames():preset==9?8192:preset==10?2048:0):0;
                    if(tailSize>0){if(tailRemaining<0)tailRemaining=tailSize;if(tailRemaining>0){int tail=Math.min(2048,tailRemaining);java.util.Arrays.fill(floats,0);writeBlock(tail);tailRemaining-=tail;continue;}}
                    long head;synchronized(lock){head=track==null?written:(track.getPlaybackHeadPosition()&0xffffffffL);}if(head>=written){success=true;break;}Thread.sleep(8);continue;}
                if(!inputEnd){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer buf=codec.getInputBuffer(index);int n=extractor.readSampleData(buf,0);if(n<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}else{codec.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){format=codec.getOutputFormat();outputFormat(format);continue;}
                if(index<0)continue;
                try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                    if(track==null)outputFormat(format);if(stopped)break;
                    int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(channels<1||channels>2)throw new Exception("3D丽音目前支持单声道和立体声音乐");
                    int encoding=format.containsKey("pcm-encoding")?format.getInteger("pcm-encoding"):AudioFormat.ENCODING_PCM_16BIT;
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new Exception("此解码器输出格式暂不支持音效");
                    int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;int frames=info.size/(bytes*channels);
                    ByteBuffer buf=codec.getOutputBuffer(index).duplicate().order(ByteOrder.nativeOrder());buf.position(info.offset);buf.limit(info.offset+info.size);
                    int skip=trimUs>info.presentationTimeUs?(int)Math.min(frames,((trimUs-info.presentationTimeUs)*rate+999999)/1000000):0;
                    buf.position(info.offset+skip*bytes*channels);frames-=skip;long pts=info.presentationTimeUs+skip*1000000L/rate;
                    if(frames>0){if(!anchored){synchronized(lock){baseUs=pts;anchored=true;}}trimUs=-1;}
                    while(frames>0&&!stopped&&seekRequest.get()<0){int count=Math.min(2048,frames);
                        for(int j=0;j<count;j++){float left=encoding==AudioFormat.ENCODING_PCM_FLOAT?buf.getFloat():buf.getShort()/32768f;float right=channels==1?left:encoding==AudioFormat.ENCODING_PCM_FLOAT?buf.getFloat():buf.getShort()/32768f;floats[j*2]=left;floats[j*2+1]=right;}
                        writeBlock(count);frames-=count;
                    }
                }}finally{codec.releaseOutputBuffer(index,false);}
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputEnd=true;
            }
        }catch(Exception e){if(!stopped)listener.failed(this,e.getMessage()==null?"音效播放失败":e.getMessage());}
        catch(LinkageError e){if(!stopped)listener.failed(this,"原版音效库不兼容此设备");}
        finally{synchronized(lock){if(track!=null){try{track.pause();track.flush();track.release();}catch(RuntimeException ignored){}track=null;}if(wake.isHeld())wake.release();}
            if(fx!=0){Native3d.destroy(fx);fx=0;}if(codec!=null)try{codec.stop();codec.release();}catch(RuntimeException ignored){}if(extractor!=null)extractor.release();}
        if(success&&!stopped)listener.completed(this);
    }
}
