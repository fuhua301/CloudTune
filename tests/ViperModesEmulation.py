"""Run the supplied arm64 DSP in an isolated CPU emulator; no APK installation.

This verifies numeric PCM processing only, not Android dynamic linking or audio output.
"""
import json, math, struct, sys, io, zipfile
from pathlib import Path
from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *

BASE=0x10000000; HEAP=0x100000000; STUB=0x30000000; STACK=0x40000000; STOP=STUB+0xF0000
uc=Uc(UC_ARCH_ARM64,UC_MODE_ARM); uc.mem_map(BASE,0x400000); uc.mem_map(HEAP,0x10000000); uc.mem_map(STUB,0x100000); uc.mem_map(STACK,0x100000)
heap=HEAP; imports={}; allocs={}; called=set()
def alloc(n):
 global heap
 n=max(16,int(n));p=heap;heap=(heap+n+15)&~15
 if heap>=HEAP+0x10000000:raise RuntimeError('test heap full')
 allocs[p]=n;return p
def put64(a,n):uc.mem_write(a,struct.pack('<Q',n&0xFFFFFFFFFFFFFFFF))
def strz(a):
 b=bytearray()
 while True:
  x=uc.mem_read(a,1)[0];a+=1
  if x==0:return bytes(b)
  b.append(x)
LIBS=Path(__file__).resolve().parents[1]/'app/src/main/jniLibs/arm64-v8a'
library=(LIBS/'libeffect0.so').read_bytes()
with io.BytesIO(library) as f:
 elf=ELFFile(f);ds=elf.get_section_by_name('.dynsym');syms={s.name:s for s in ds.iter_symbols()}
 for seg in elf.iter_segments():
  if seg['p_type']=='PT_LOAD':uc.mem_write(BASE+seg['p_vaddr'],seg.data())
 def external(name):
  for addr,n in imports.items():
   if n==name:return addr
  addr=STUB+16*len(imports);imports[addr]=name;uc.mem_write(addr,b'\xc0\x03\x5f\xd6');return addr
 for sec in elf.iter_sections():
  if sec['sh_type']!='SHT_RELA':continue
  for r in sec.iter_relocations():
   dest=BASE+r['r_offset'];typ=r['r_info_type'];add=r['r_addend']
   if typ==1027:put64(dest,BASE+add)
   elif typ in [257,1025,1026]:
    s=ds.get_symbol(r['r_info_sym']);value=BASE+s['st_value'] if s['st_shndx']!='SHN_UNDEF' else external(s.name)
    put64(dest,value+add)
   else:raise RuntimeError('unsupported relocation '+str(typ))
 table={n:BASE+s['st_value'] for n,s in syms.items() if s['st_shndx']!='SHN_UNDEF'}
def hook(u,addr,size,data):
 if addr==STOP:u.emu_stop();return
 if addr not in imports:return
 n=imports[addr];called.add(n);a=u.reg_read(UC_ARM64_REG_X0);b=u.reg_read(UC_ARM64_REG_X1);c=u.reg_read(UC_ARM64_REG_X2);result=0
 if n in ['_Znwm','_Znam','malloc']:result=alloc(a)
 elif n=='calloc':result=alloc(a*b)
 elif n in ['_ZdlPv','_ZdaPv','free']:pass
 elif n in ['memset','__memset_chk']:u.mem_write(a,bytes([b&255])*c);result=a
 elif n in ['memcpy','memmove','__memcpy_chk','__memmove_chk']:u.mem_write(a,bytes(u.mem_read(b,c)));result=a
 elif n=='realloc':
  result=alloc(b)
  if a:u.mem_write(result,bytes(u.mem_read(a,min(allocs.get(a,0),b))))
 elif n=='memalign':result=alloc(b)
 elif n=='getpagesize':result=4096
 elif n=='posix_memalign':put64(a,alloc(c));result=0
 elif n=='strlen':result=len(strz(a))
 elif n=='dlopen':result=2
 elif n=='dlclose':pass
 elif n=='dlsym':result=table.get(strz(b).decode(),0)
 elif n=='JNI_GetStringUTFChars':result=b
 elif n in ['JNI_ReleaseStringUTFChars','JNI_ReleaseFloatArrayElements']:pass
 elif n=='JNI_GetArrayLength':result=struct.unpack('<I',u.mem_read(b,4))[0]
 elif n=='JNI_GetFloatArrayElements':result=b+8
 elif n=='JNI_FindClass':result=1
 elif n=='JNI_ThrowNew':raise RuntimeError('JNI exception: '+strz(c).decode())
 elif n in ['sinh','tan','pow','exp2','log2','exp','log','log10','cos','sin','sqrt','floor','ceil']:
  x=struct.unpack('<d',struct.pack('<Q',u.reg_read(UC_ARM64_REG_D0)))[0];y=struct.unpack('<d',struct.pack('<Q',u.reg_read(UC_ARM64_REG_D1)))[0];v=math.pow(x,y) if n=='pow' else getattr(math,n)(x);u.reg_write(UC_ARM64_REG_D0,struct.unpack('<Q',struct.pack('<d',v))[0])
 elif n in ['floorf','ceilf','sqrtf','expf','logf','log10f','sinf','cosf','powf','fabsf']:
  x=struct.unpack('<f',struct.pack('<I',u.reg_read(UC_ARM64_REG_S0)))[0];y=struct.unpack('<f',struct.pack('<I',u.reg_read(UC_ARM64_REG_S1)))[0]
  fn={'floorf':math.floor,'ceilf':math.ceil,'sqrtf':math.sqrt,'expf':math.exp,'logf':math.log,'log10f':math.log10,'sinf':math.sin,'cosf':math.cos,'fabsf':abs}
  v=math.pow(x,y) if n=='powf' else fn[n](x);u.reg_write(UC_ARM64_REG_S0,struct.unpack('<I',struct.pack('<f',v))[0])
 else:raise RuntimeError('unimplemented external '+n)
 u.reg_write(UC_ARM64_REG_X0,result);u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
uc.hook_add(UC_HOOK_CODE,hook,begin=STUB,end=STUB+0x100000)
def call(name,*args,fp=None):
 uc.reg_write(UC_ARM64_REG_SP,STACK+0xF0000);uc.reg_write(UC_ARM64_REG_LR,STOP)
 for i,a in enumerate(args):uc.reg_write(UC_ARM64_REG_X0+i,a)
 if fp is not None:uc.reg_write(UC_ARM64_REG_S0,struct.unpack('<I',struct.pack('<f',fp))[0])
 uc.emu_start(table[name],STOP+4,count=20000000)
 if uc.reg_read(UC_ARM64_REG_PC)!=STOP:raise RuntimeError('instruction budget exhausted: '+name)
 return uc.reg_read(UC_ARM64_REG_X0)
def render(enabled,rate=48000,channels=2,loudness=False):
 handle=call('EFFX_CreateEffectInstance');assert handle
 call('EFFX_SetSamplingRate',handle,rate);call('EFFX_SetChannels',handle,channels)
 call('EFFX_SetSurroundEnable',handle,int(enabled));call('EFFX_SetSurroundValue',handle,fp=1.0)
 call('EFFX_SetVolumeEnable',handle,int(loudness));call('EFFX_SetVolumeRatio',handle,fp=2.0);call('EFFX_SetVolumeMaxGain',handle,fp=8.0);call('EFFX_SetExpectVolume',handle,fp=1.0);call('EFFX_Reset',handle)
 frames=rate//8;signal=[]
 for i in range(frames):
  l=.2*math.sin(i*2*math.pi*997/rate)
  signal.extend([l] if channels==1 else [l,.11*math.sin(i*2*math.pi*431/rate)])
 ptr=alloc(2048*channels*4);out=[]
 for start in range(0,frames,1024):
  count=min(1024,frames-start);block=signal[start*channels:(start+count)*channels];uc.mem_write(ptr,struct.pack('<%df'%len(block),*block))
  result=call('EFFX_ProcessBufferFloat',handle,ptr,count);assert result==count,(count,result)
  out.extend(struct.unpack('<%df'%(count*channels),uc.mem_read(ptr,count*channels*4)))
 call('EFFX_ReleaseEffectInstance',handle)
 assert len(out)==len(signal) and all(math.isfinite(x) and abs(x)<=1.001 for x in out)
 return signal,out

V4=0x60000000;uc.mem_map(V4,0x200000)
blob=(LIBS/'libviper4android.so').read_bytes()
with io.BytesIO(blob) as f:
 elf=ELFFile(f);ds=elf.get_section_by_name('.dynsym')
 for seg in elf.iter_segments():
  if seg['p_type']=='PT_LOAD':uc.mem_write(V4+seg['p_vaddr'],seg.data())
 for sec in elf.iter_sections():
  if sec['sh_type']!='SHT_RELA':continue
  for r in sec.iter_relocations():
   typ=r['r_info_type'];dest=V4+r['r_offset'];add=r['r_addend']
   if typ==1027:put64(dest,V4+add)
   elif typ in [257,1025,1026]:
    symbol=ds.get_symbol(r['r_info_sym']);val=external(symbol.name) if symbol['st_shndx']=='SHN_UNDEF' else V4+symbol['st_value'];put64(dest,val+add)
   else:raise RuntimeError(typ)
 table.update({'V4_'+x.name:V4+x['st_value'] for x in ds.iter_symbols() if x['st_shndx']!='SHN_UNDEF'})
parambuf=alloc(12)
def setp(handle,param,value):
 uc.mem_write(parambuf,struct.pack('<iii',param,4,value));ret=call('V4_ViPERCommand',handle,2,12,parambuf,0,0);assert ret==0,ret
presets={'bypass':[],'bass':[(65575,0),(65576,80),(65577,350),(65574,1)],'clarity':[(65579,0),(65580,250),(65578,1)],'live':[(65560,65),(65561,90),(65562,40),(65563,20),(65564,85),(65559,1)],'wide':[(65554,140),(65555,100),(65556,300),(65553,1),(65558,12),(65557,1)],'tube':[(65583,1)],'analog':[(65585,1),(65584,1)]}
checks=[]
for label,params in presets.items():
 h=call('V4_CreateViPER');setp(h,0x9002,48000);setp(h,65537,1);setp(h,0x9003,1);setp(h,65588,98)
 for param,value in params:setp(h,param,value)
 ptr=alloc(2048*2*4);delta=0;peak=0
 for block in range(8):
  source=[.12*math.sin((i+block*4096)*.01)+.07*math.sin((i+block*4096)*.17) for i in range(4096)];uc.mem_write(ptr,struct.pack('<4096f',*source));n=call('V4_ViPERProcessFloat',h,ptr,2048);out=struct.unpack('<4096f',uc.mem_read(ptr,16384));assert n==2048,n;assert all(math.isfinite(x) for x in out);delta=max(delta,max(abs(a-b) for a,b in zip(source,out)));peak=max(peak,max(abs(x) for x in out))
 call('V4_ReleaseViPER',h)
 if label=='bypass':baseline=out
 else:
  effectDelta=max(abs(a-b) for a,b in zip(baseline,out));assert effectDelta>.001,(label,effectDelta);checks.append({'preset':label,'effect_difference_vs_bypass':effectDelta,'peak':peak})
print(json.dumps(checks,indent=2))
