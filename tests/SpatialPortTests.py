"""Numeric integration checks for the bundled HIFI / rotating HRTF ports.
No Android device, decoder, loader or listening test is implied.
"""
from pathlib import Path
# Reuse only loader/JNI fixture setup, without executing its preset matrix.
fixture=Path(__file__).with_name('OriginalDspEmulation.py').read_text().split('checks=[]')[0]
exec(compile(fixture,str(Path(__file__).with_name('OriginalDspEmulation.py')),'exec'))
checks=[]
def processed(mode,rate,pattern):
 h=call(prefix+'create',env,1,*paths,rate,0,mode);assert h
 total=12288;offset=0;out=[];k=0
 while offset<total:
  n=min(pattern[k%len(pattern)],total-offset);k+=1
  signal=[]
  for i in range(n):
   t=i+offset;signal.extend([.12*math.sin(t*.12)+.04*math.sin(t*.023),.1*math.sin(t*.071)])
  raw=struct.pack('<%df'%len(signal),*signal);uc.mem_write(array+8,raw)
  count=call(prefix+'process',env,1,h,array,n);assert 0<=count<=2048
  samples=struct.unpack('<%df'%(count*2),uc.mem_read(array+8,count*8))
  assert all(math.isfinite(x) and abs(x)<=1.01 for x in samples)
  out.extend(samples);offset+=n
 call(prefix+'destroy',env,1,h)
 return out
for rate in [44100,48000]:
 reference=processed(10,rate,[2048]);fragmented=processed(10,rate,[1,17,257,511,129])
 assert len(reference)==len(fragmented)==24576
 error=max(abs(a-b) for a,b in zip(reference,fragmented));assert error<1e-6,error
 energy=sum(x*x for x in reference);assert energy>1
 stereo=max(abs(reference[i]-reference[i+1]) for i in range(0,len(reference),2));assert stereo>.001
 checks.append(dict(preset='3D旋转',rate=rate,chunk_invariance_error=error,energy=energy,stereo_difference=stereo))
 for pattern in [[2048],[17,257,511,129]]:
  hifi=processed(9,rate,pattern);energy=sum(x*x for x in hifi);assert energy>1
  checks.append(dict(preset='HIFI现场',rate=rate,chunk_pattern=pattern,finite_output=True,energy=energy))
# Verify original HRTF renderer actually changes its response with source angle.
def direction(angle):
 h=call('CreateViPER3DRender');assert call('_ZN13ViPER3DRender14ConfigInstanceEii',h,44100,256)==1
 for i,v in enumerate([0,angle-17,1.4,0,angle+17,1.4]):uc.reg_write(UC_ARM64_REG_S0+i,struct.unpack('<I',struct.pack('<f',v))[0])
 call('_ZN13ViPER3DRender17SetSourcePositionEffffff',h)
 output=alloc(2048);inp=alloc(2048)
 for b in range(8):
  data=[.1*math.sin((i//2+b*256)*.15) for i in range(512)];uc.mem_write(inp,struct.pack('<512f',*data));assert call('_ZN13ViPER3DRender16RenderBlockFloatEPfS0_',h,inp,output)==1
 result=struct.unpack('<512f',uc.mem_read(output,2048));call('_ZN13ViPER3DRender7ReleaseEv',h);return result
front=direction(0)
for angle in [90,180,270]:
 turned=direction(angle);delta=max(abs(a-b) for a,b in zip(front,turned));assert delta>.001,(angle,delta)
 checks.append(dict(original_renderer_angle=angle,difference_from_front=delta))
print(json.dumps(dict(result='passed',checks=checks,scope='Original ARM64 DSP and compiled JNI under CPU emulation with loader/libc/JNI mocks; no phone playback test'),ensure_ascii=False,indent=2))
