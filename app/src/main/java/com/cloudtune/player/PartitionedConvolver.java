package com.cloudtune.player;

import java.io.*;

/** Streaming stereo overlap-add convolution. Fixed 256-frame latency, no per-block allocations. */
final class PartitionedConvolver {
    static final int BLOCK=256,N=512;
    private final int partitions,kernelFrames;private int cursor,head;
    private final float[][][] hr,hi,xr,xi;
    private final float[][] input=new float[2][BLOCK],output=new float[2][BLOCK],overlap=new float[2][BLOCK];
    private final float[] re=new float[N],im=new float[N],yr=new float[N],yi=new float[N],cos=new float[N/2],sin=new float[N/2];
    private final int[] reverse=new int[N];
    PartitionedConvolver(InputStream wave,int targetRate)throws IOException{
        float[][] kernel=readWave(wave,targetRate);kernelFrames=kernel[0].length;partitions=(kernelFrames+BLOCK-1)/BLOCK;
        hr=new float[2][partitions][N];hi=new float[2][partitions][N];xr=new float[2][partitions][N];xi=new float[2][partitions][N];
        for(int i=0;i<N;i++){int v=i,r=0;for(int b=0;b<9;b++){r=(r<<1)|(v&1);v>>=1;}reverse[i]=r;}
        for(int i=0;i<N/2;i++){cos[i]=(float)Math.cos(2*Math.PI*i/N);sin[i]=(float)Math.sin(2*Math.PI*i/N);}
        for(int c=0;c<2;c++)for(int p=0;p<partitions;p++){java.util.Arrays.fill(re,0);java.util.Arrays.fill(im,0);for(int i=0;i<BLOCK&&p*BLOCK+i<kernelFrames;i++)re[i]=kernel[c][p*BLOCK+i];fft(re,im,false);System.arraycopy(re,0,hr[c][p],0,N);System.arraycopy(im,0,hi[c][p],0,N);}
    }
    int tailFrames(){return kernelFrames+BLOCK;}
    void process(float[] interleaved,int frames){
        for(int i=0;i<frames;i++){
            input[0][cursor]=interleaved[2*i];input[1][cursor]=interleaved[2*i+1];interleaved[2*i]=output[0][cursor];interleaved[2*i+1]=output[1][cursor];
            if(++cursor==BLOCK){cursor=0;render();}
        }
    }
    private void render(){
        for(int c=0;c<2;c++){
            System.arraycopy(input[c],0,re,0,BLOCK);java.util.Arrays.fill(re,BLOCK,N,0);java.util.Arrays.fill(im,0);fft(re,im,false);
            System.arraycopy(re,0,xr[c][head],0,N);System.arraycopy(im,0,xi[c][head],0,N);java.util.Arrays.fill(yr,0);java.util.Arrays.fill(yi,0);
            for(int p=0;p<partitions;p++){int q=(head-p+partitions)%partitions;float[] ar=xr[c][q],ai=xi[c][q],br=hr[c][p],bi=hi[c][p];for(int k=0;k<N;k++){yr[k]+=ar[k]*br[k]-ai[k]*bi[k];yi[k]+=ar[k]*bi[k]+ai[k]*br[k];}}
            fft(yr,yi,true);for(int i=0;i<BLOCK;i++){output[c][i]=yr[i]+overlap[c][i];overlap[c][i]=yr[i+BLOCK];}
        }head=(head+1)%partitions;
    }
    private void fft(float[] r,float[] im,boolean inverse){
        for(int i=0;i<N;i++){int j=reverse[i];if(j>i){float t=r[i];r[i]=r[j];r[j]=t;t=im[i];im[i]=im[j];im[j]=t;}}
        for(int size=2;size<=N;size*=2){int half=size/2,step=N/size;for(int start=0;start<N;start+=size)for(int j=0;j<half;j++){
            int a=start+j,b=a+half,k=j*step;float wr=cos[k],wi=inverse?sin[k]:-sin[k],tr=wr*r[b]-wi*im[b],ti=wr*im[b]+wi*r[b];r[b]=r[a]-tr;im[b]=im[a]-ti;r[a]+=tr;im[a]+=ti;
        }}if(inverse)for(int i=0;i<N;i++){r[i]/=N;im[i]/=N;}
    }
    static float[][] readWave(InputStream in,int targetRate)throws IOException{
        if(targetRate<8000||targetRate>192000)throw new IOException("不支持此脉冲采样率");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] tmp=new byte[4096];int count;
        while((count=in.read(tmp))!=-1){if(out.size()+count>1024*1024)throw new IOException("脉冲文件过大");out.write(tmp,0,count);}byte[] b=out.toByteArray();
        if(b.length<44||!tag(b,0,"RIFF")||!tag(b,8,"WAVE"))throw new IOException("无效的黑钻脉冲文件");int channels=0,rate=0,bits=0,data=-1,size=0,format=0;
        for(int p=12;p+8<=b.length;){long n=u32(b,p+4);if(n>b.length-p-8)throw new IOException("脉冲文件截断");if(tag(b,p,"fmt ")&&n>=16){format=u16(b,p+8);channels=u16(b,p+10);rate=(int)u32(b,p+12);bits=u16(b,p+22);}if(tag(b,p,"data")){data=p+8;size=(int)n;}p+=(int)n+8+((int)n&1);}
        if(format!=1||(channels!=1&&channels!=2)||bits!=16||rate<8000||rate>192000||data<0||size<2*channels||size%(channels*2)!=0)throw new IOException("不支持此黑钻脉冲格式");
        int sourceFrames=size/(channels*2),frames=Math.max(1,(int)Math.ceil((double)sourceFrames*targetRate/rate));if(frames>16384)throw new IOException("脉冲长度超过限制");float[][] kernel=new float[2][frames];
        for(int c=0;c<2;c++)for(int i=0;i<frames;i++){double pos=(double)i*rate/targetRate;int a=(int)pos,next=a+1;float t=(float)(pos-a);float left=a<sourceFrames?(short)u16(b,data+(a*channels+Math.min(c,channels-1))*2)/32768f:0;float right=next<sourceFrames?(short)u16(b,data+(next*channels+Math.min(c,channels-1))*2)/32768f:0;kernel[c][i]=left+(right-left)*t;}
        return kernel;
    }
    private static boolean tag(byte[] b,int p,String tag){if(p+4>b.length)return false;for(int i=0;i<4;i++)if(b[p+i]!=(byte)tag.charAt(i))return false;return true;}
    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static long u32(byte[] b,int p){return (u16(b,p)|((long)u16(b,p+2)<<16));}
}
