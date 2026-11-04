package com.cloudtune.player;
import java.io.*;
import java.util.*;

/** Compare the streaming FFT implementation with an independent direct FIR. */
public final class ConvolverTests {
    static int checks;
    static void verify(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static byte[] wave(short[] samples,int channels,int rate)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        d.writeBytes("RIFF");le(d,36+samples.length*2,4);d.writeBytes("WAVEfmt ");le(d,16,4);le(d,1,2);le(d,channels,2);le(d,rate,4);le(d,rate*channels*2,4);le(d,channels*2,2);le(d,16,2);d.writeBytes("data");le(d,samples.length*2,4);for(short s:samples)le(d,s,2);return b.toByteArray();
    }
    static void le(DataOutputStream d,int v,int n)throws Exception{for(int i=0;i<n;i++)d.writeByte(v>>(8*i));}
    static void run(byte[] bytes,int rate,String label)throws Exception{
        float[][] h=PartitionedConvolver.readWave(new ByteArrayInputStream(bytes),rate);
        PartitionedConvolver fx=new PartitionedConvolver(new ByteArrayInputStream(bytes),rate);
        int frames=1203,total=frames+fx.tailFrames()+80;float[] src=new float[frames*2],out=new float[total*2];Random random=new Random(7301);
        for(int i=0;i<src.length;i++)src[i]=(random.nextFloat()-.5f)*.2f;
        System.arraycopy(src,0,out,0,src.length);
        int[] chunks={1,17,513,255,2048,31};int p=0,k=0;
        while(p<total){int n=Math.min(chunks[k++%chunks.length],total-p);float[] block=Arrays.copyOfRange(out,p*2,(p+n)*2);fx.process(block,n);System.arraycopy(block,0,out,p*2,n*2);p+=n;}
        double max=0,energy=0;
        for(int i=0;i<total;i++)for(int c=0;c<2;c++){
            double expected=0;int t=i-PartitionedConvolver.BLOCK;
            for(int j=Math.max(0,t-frames+1);j<h[c].length&&j<=t;j++)expected+=h[c][j]*src[(t-j)*2+c];
            double actual=out[i*2+c];verify(!Double.isNaN(actual)&&!Double.isInfinite(actual),label+" finite");max=Math.max(max,Math.abs(actual-expected));energy+=actual*actual;
        }
        verify(max<.0001,label+" direct FIR mismatch "+max);verify(energy>0,label+" silent");
        System.out.println(label+" rate="+rate+" taps="+h[0].length+" max_error="+max);
    }
    public static void main(String[] args)throws Exception{
        run(wave(new short[]{32767},1,44100),44100,"identity and latency");
        run(wave(new short[]{18000,-8000,4000,1000,-2000,6000},2,48000),48000,"independent stereo");
        for(String path:args)for(int rate:new int[]{44100,48000})run(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),rate,new File(path).getName());
        try{new PartitionedConvolver(new ByteArrayInputStream(new byte[48]),48000);throw new AssertionError("bad header accepted");}catch(IOException ok){checks++;}
        try{new PartitionedConvolver(new ByteArrayInputStream(wave(new short[]{1},1,44100)),0);throw new AssertionError("bad rate accepted");}catch(IOException ok){checks++;}
        System.out.println("PASS: "+checks+" convolution assertions; includes tail, latency, chunk boundaries, stereo and resampling");
    }
}
