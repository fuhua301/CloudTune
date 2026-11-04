/* JNI bridge for the original EFFX and ViPER DSP libraries supplied with this project. */
#include "jni.h"
#include <stddef.h>
extern void *dlopen(const char *,int);extern void *dlsym(void *,const char *);
extern const char *dlerror(void);extern int dlclose(void *);
extern void *calloc(size_t,size_t);extern void free(void *);
typedef void *(*Create)(void);typedef void (*Destroy)(void *);
typedef void (*SetInt)(void *,int);typedef void (*SetFloat)(void *,float);
typedef int (*Process)(void *,float *,int);
typedef int (*Active)(void *,int,int,int);
typedef int (*Config3d)(void *,int,int);
typedef int (*Render3d)(void *,float *,float *);
typedef void (*Position3d)(void *,float,float,float,float,float,float);
typedef int (*Command)(void *,unsigned,unsigned,void *,unsigned *,void *);
typedef struct {
 void *runtime,*lib,*engine,*vlib,*vengine;int rateValue,enabled,preset;
 Destroy release,reset,vrelease;Create vcreate;Command command;Process process,vprocess;
 SetInt rate,channels,surround,volume;SetFloat width,ratio,maxGain,expect;
 void *hengine,*sengine;Create hcreate,screate;Destroy hrelease,srelease;Active hactive;Process hprocess;Config3d sconfig;Render3d srender;Position3d position;
 float spatialIn[512],spatialOut[512],queue[16384],inputPrevious[2],outputPrevious[2];
 int spatialCursor,queueHead,queueCount,inputStarted,outputStarted;double inputNext,outputNext,angle;
} Fx;
static void fail(JNIEnv *e,const char *m){jclass c=(*e)->FindClass(e,"java/lang/IllegalStateException");if(c)(*e)->ThrowNew(e,c,m?m:"内置音效加载失败");}
static void dispose(Fx *f){if(!f)return;if(f->hengine&&f->hrelease)f->hrelease(f->hengine);if(f->sengine&&f->srelease)f->srelease(f->sengine);if(f->vengine&&f->vrelease)f->vrelease(f->vengine);if(f->engine&&f->release)f->release(f->engine);if(f->vlib)dlclose(f->vlib);if(f->lib)dlclose(f->lib);if(f->runtime)dlclose(f->runtime);free(f);}
static int parameter(Fx *f,int id,int value){int packet[3]={id,4,value};return f->command(f->vengine,2,sizeof(packet),packet,0,0);}
/* Adapt arbitrary source blocks to the original renderer's 256-frame / 44.1kHz contract.
   Streaming fractional positions are preserved across calls. The bounded stereo queue
   provides 512 source frames of latency; no audio-thread allocations or Java callbacks. */
static int spatial(Fx *f,float *samples,int frames){
 for(int i=0;i<frames;i++){
  float l=samples[i*2],r=samples[i*2+1];
  if(!f->inputStarted){f->inputPrevious[0]=l;f->inputPrevious[1]=r;f->inputStarted=1;}
  while(f->inputNext<=0.000000001){
   double a=f->inputNext+1.0;int k=f->spatialCursor*2;
   f->spatialIn[k]=f->inputPrevious[0]+(l-f->inputPrevious[0])*a;
   f->spatialIn[k+1]=f->inputPrevious[1]+(r-f->inputPrevious[1])*a;
   if(++f->spatialCursor==256){
    f->spatialCursor=0;f->position(f->sengine,0,(float)(f->angle-17),1.4f,0,(float)(f->angle+17),1.4f);
    if(!f->srender(f->sengine,f->spatialIn,f->spatialOut))return -1;
    f->angle+=360.0*256.0/(44100.0*8.0);if(f->angle>=360)f->angle-=360;
    for(int t=0;t<256;t++){
     float x=f->spatialOut[t*2],y=f->spatialOut[t*2+1];
     if(!f->outputStarted){f->outputPrevious[0]=x;f->outputPrevious[1]=y;f->outputStarted=1;}
     while(f->outputNext<=0.000000001){
      if(f->queueCount>=8192)return -1;int q=((f->queueHead+f->queueCount)%8192)*2;double b=f->outputNext+1.0;
      f->queue[q]=f->outputPrevious[0]+(x-f->outputPrevious[0])*b;
      f->queue[q+1]=f->outputPrevious[1]+(y-f->outputPrevious[1])*b;f->queueCount++;
      f->outputNext+=44100.0/f->rateValue;
     }
     f->outputNext-=1.0;f->outputPrevious[0]=x;f->outputPrevious[1]=y;
    }
   }
   f->inputNext+=(double)f->rateValue/44100.0;
  }
  f->inputNext-=1.0;f->inputPrevious[0]=l;f->inputPrevious[1]=r;
 }
 if(f->queueCount<frames)return -1;
 for(int i=0;i<frames;i++){int q=f->queueHead*2;samples[i*2]=f->queue[q];samples[i*2+1]=f->queue[q+1];f->queueHead=(f->queueHead+1)%8192;f->queueCount--;}
 return frames;
}
static int setup(JNIEnv *e,Fx *f,int enabled,int loudness,int preset){
 if(preset<0||preset>10||preset==8){fail(e,"无效的音效模式");return 0;}
 f->enabled=enabled;f->preset=preset;
 f->surround(f->engine,enabled&&preset==0);f->width(f->engine,1.0f);
 f->volume(f->engine,enabled&&loudness);f->reset(f->engine);
 if(f->vengine){f->vrelease(f->vengine);f->vengine=0;}
 if(f->hengine){f->hrelease(f->hengine);f->hengine=0;}
 if(f->sengine){f->srelease(f->sengine);f->sengine=0;}
 if(!enabled||preset<2)return 1;
 if(f->rateValue!=44100&&f->rateValue!=48000){fail(e,"此蝰蛇模式目前支持 44.1 / 48 kHz 音频，请尝试3D丽音或黑钻");return 0;}
 if(preset==9){
  f->hengine=f->hcreate();if(!f->hengine||!f->hactive(f->hengine,f->rateValue,2,1)){fail(e,"HIFI现场初始化失败");return 0;}return 1;
 }
 if(preset==10){
  f->sengine=f->screate();if(!f->sengine||!f->sconfig(f->sengine,44100,256)){fail(e,"3D旋转初始化失败");return 0;}
  f->spatialCursor=f->queueHead=f->inputStarted=f->outputStarted=0;f->queueCount=512;f->inputNext=f->outputNext=f->angle=0;
  for(int i=0;i<16384;i++)f->queue[i]=0;return 1;
 }
 f->vengine=f->vcreate();if(!f->vengine){fail(e,"蝰蛇引擎初始化失败");return 0;}
 if(parameter(f,0x9002,f->rateValue)||parameter(f,65537,1)||parameter(f,0x9003,1)||parameter(f,65588,98)){fail(e,"蝰蛇引擎参数初始化失败");return 0;}
 // All amounts below are local presets using the original engine's command protocol.
 #define P(id,value) if(parameter(f,id,value)){fail(e,"蝰蛇参数设置失败");return 0;}
 if(preset==2){P(65560,65);P(65561,90);P(65562,40);P(65563,20);P(65564,85);P(65559,1);}
 if(preset==3){P(65575,0);P(65576,80);P(65577,350);P(65574,1);}
 if(preset==4){P(65579,0);P(65580,250);P(65578,1);}
 if(preset==5){P(65554,140);P(65555,100);P(65556,300);P(65553,1);P(65558,12);P(65557,1);}
 if(preset==6){P(65583,1);}
 if(preset==7){P(65585,1);P(65584,1);}
 #undef P
 return 1;
}
#define LOAD(field,name) f->field=(void*)dlsym(f->lib,name);if(!f->field){dispose(f);fail(e,"内置 EFFX 接口不完整");return 0;}
#define VLOAD(field,name) f->field=(void*)dlsym(f->vlib,name);if(!f->field){dispose(f);fail(e,"内置蝰蛇接口不完整");return 0;}
JNIEXPORT jlong JNICALL Java_com_cloudtune_player_Native3d_create(JNIEnv *e,jclass cls,jstring runtime,jstring library,jstring viper,jint sampleRate,jboolean loudness,jint preset){
 (void)cls;Fx *f=calloc(1,sizeof(Fx));if(!f){fail(e,"音效内存不足");return 0;}
 const char *p=(*e)->GetStringUTFChars(e,runtime,0);if(!p){dispose(f);return 0;}f->runtime=dlopen(p,2);(*e)->ReleaseStringUTFChars(e,runtime,p);
 if(!f->runtime){fail(e,dlerror());dispose(f);return 0;}
 p=(*e)->GetStringUTFChars(e,library,0);if(!p){dispose(f);return 0;}f->lib=dlopen(p,2);(*e)->ReleaseStringUTFChars(e,library,p);
 if(!f->lib){fail(e,dlerror());dispose(f);return 0;}
 p=(*e)->GetStringUTFChars(e,viper,0);if(!p){dispose(f);return 0;}f->vlib=dlopen(p,2);(*e)->ReleaseStringUTFChars(e,viper,p);
 if(!f->vlib){fail(e,dlerror());dispose(f);return 0;}
 Create create=(void*)dlsym(f->lib,"EFFX_CreateEffectInstance");if(!create){dispose(f);fail(e,"内置3D丽音接口不完整");return 0;}
 LOAD(release,"EFFX_ReleaseEffectInstance");LOAD(rate,"EFFX_SetSamplingRate");LOAD(channels,"EFFX_SetChannels");
 LOAD(surround,"EFFX_SetSurroundEnable");LOAD(width,"EFFX_SetSurroundValue");LOAD(volume,"EFFX_SetVolumeEnable");
 LOAD(ratio,"EFFX_SetVolumeRatio");LOAD(maxGain,"EFFX_SetVolumeMaxGain");LOAD(expect,"EFFX_SetExpectVolume");LOAD(reset,"EFFX_Reset");LOAD(process,"EFFX_ProcessBufferFloat");
 LOAD(hcreate,"CreateVirtualizer");LOAD(hrelease,"_ZN11Virtualizer7ReleaseEv");LOAD(hactive,"_ZN11Virtualizer6ActiveEiii");LOAD(hprocess,"_ZN11Virtualizer12ProcessFloatEPfi");
 LOAD(screate,"CreateViPER3DRender");LOAD(srelease,"_ZN13ViPER3DRender7ReleaseEv");LOAD(sconfig,"_ZN13ViPER3DRender14ConfigInstanceEii");LOAD(srender,"_ZN13ViPER3DRender16RenderBlockFloatEPfS0_");LOAD(position,"_ZN13ViPER3DRender17SetSourcePositionEffffff");
 VLOAD(vcreate,"CreateViPER");VLOAD(vrelease,"ReleaseViPER");VLOAD(command,"ViPERCommand");VLOAD(vprocess,"ViPERProcessFloat");
 f->engine=create();if(!f->engine){dispose(f);fail(e,"内置3D引擎初始化失败");return 0;}
 f->rateValue=sampleRate;f->rate(f->engine,sampleRate);f->channels(f->engine,2);f->ratio(f->engine,2.0f);f->expect(f->engine,1.0f);f->maxGain(f->engine,8.0f);
 if(!setup(e,f,1,loudness,preset)){dispose(f);return 0;}return (jlong)(uintptr_t)f;
}
JNIEXPORT void JNICALL Java_com_cloudtune_player_Native3d_configure(JNIEnv *e,jclass c,jlong h,jboolean on,jboolean gain,jint preset){(void)c;Fx *f=(Fx*)(uintptr_t)h;if(f)setup(e,f,on,gain,preset);}
JNIEXPORT jint JNICALL Java_com_cloudtune_player_Native3d_process(JNIEnv *e,jclass c,jlong h,jfloatArray buffer,jint frames){
 (void)c;Fx *f=(Fx*)(uintptr_t)h;if(!f||frames<0||frames>2048||(*e)->GetArrayLength(e,buffer)<4096){fail(e,"无效的音效输入帧数");return -1;}
 jfloat *samples=(*e)->GetFloatArrayElements(e,buffer,0);if(!samples)return -1;int count=frames;
 if(f->enabled){if(f->hengine)count=f->hprocess(f->hengine,samples,frames);else if(f->sengine)count=spatial(f,samples,frames);else if(f->vengine)count=f->vprocess(f->vengine,samples,frames);if(count>=0&&count<=2048)count=f->process(f->engine,samples,count);}
 (*e)->ReleaseFloatArrayElements(e,buffer,samples,0);if(count<0||count>2048){fail(e,"原版引擎输出帧数无效");return -1;}return count;
}
JNIEXPORT void JNICALL Java_com_cloudtune_player_Native3d_destroy(JNIEnv *e,jclass c,jlong h){(void)e;(void)c;dispose((Fx*)(uintptr_t)h);}
