"""Build the app-owned ARM64 JNI bridge with `pip install ziglang==0.13.0`.

The tiny link stubs below only record Android's public libc/libdl dependencies;
they are NOT packaged. Android resolves them to its real system libraries.
"""
from pathlib import Path
import subprocess,sys,os
ROOT=Path(__file__).resolve().parent
BUILD=ROOT/'build';BUILD.mkdir(exist_ok=True)
OUT=ROOT.parent/'app/src/main/jniLibs/arm64-v8a';OUT.mkdir(parents=True,exist_ok=True)
CC=[sys.executable,'-m','ziglang','cc','-target','aarch64-linux-android','-shared','-nostdlib','-fPIC','-ffreestanding','-fno-stack-protector','-Wl,-z,max-page-size=16384','-Wl,--hash-style=both']
def run(args):subprocess.run(args,check=True)
for lib,src in [('dl','void *dlopen(const char*a,int b){return 0;} void*dlsym(void*a,const char*b){return 0;} const char*dlerror(void){return 0;} int dlclose(void*a){return 0;}'),('c','typedef __SIZE_TYPE__ size_t;void*calloc(size_t a,size_t b){return 0;} void free(void*a){}')]:
 p=BUILD/(lib+'.c');p.write_text(src)
 run(CC+[str(p),'-Wl,-soname,lib'+lib+'.so','-o',str(BUILD/('lib'+lib+'.so'))])
run(CC+['-O2','-I'+str(ROOT/'include'),str(ROOT/'cloudtune_fx.c'),'-Wl,--no-as-needed',str(BUILD/'libdl.so'),str(BUILD/'libc.so'),'-Wl,-soname,libcloudtune_fx.so','-o',str(OUT/'libcloudtune_fx.so')])
print(OUT/'libcloudtune_fx.so')
