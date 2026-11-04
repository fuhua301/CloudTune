#!/usr/bin/env python3
"""Java 17 + Python 3 build path; independent of the optional AIDE/Gradle path."""
from pathlib import Path
import hashlib,subprocess,urllib.request,zipfile,shutil,os
ROOT=Path(__file__).resolve().parent
TOOLS=ROOT/'.tools';TOOLS.mkdir(exist_ok=True)
URLS={
'android28.jar':'https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-28/android.jar',
'ecj.jar':'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar',
'r8.jar':'https://storage.googleapis.com/r8-releases/raw/8.3.37/r8.jar',
'apktool.jar':'https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar',
'signer.jar':'https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar',
'json.jar':'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
}
def run(*args):subprocess.run([str(x) for x in args],cwd=ROOT,check=True)
for name,url in URLS.items():
    p=TOOLS/name
    if not p.exists():
        print('Downloading',name,flush=True)
        tmp=p.with_suffix('.part');urllib.request.urlretrieve(url,tmp);tmp.replace(p)
    print(name,hashlib.sha256(p.read_bytes()).hexdigest())
B=ROOT/'build';C=B/'classes';D=B/'dex';A=B/'apk';DIST=ROOT/'dist'
if B.exists():shutil.rmtree(B)
for p in (C,D,A,DIST):p.mkdir(parents=True,exist_ok=True)
SDK=TOOLS/'android28.jar';ZX=ROOT/'app/libs/zxing-core.jar'
cp=os.pathsep.join(map(str,[SDK,ZX]))
run('java','-jar',TOOLS/'ecj.jar','-source','1.8','-target','1.8','-encoding','UTF-8','-cp',cp,'-d',C,*sorted((ROOT/'app/src/main/java').rglob('*.java')))
with zipfile.ZipFile(B/'classes.jar','w') as z:
    for p in C.rglob('*.class'):z.write(p,p.relative_to(C))
run('java','-cp',TOOLS/'r8.jar','com.android.tools.r8.D8','--min-api','23','--lib',SDK,'--output',D,B/'classes.jar',ZX)
shutil.copyfile(D/'classes.dex',A/'classes.dex');shutil.copyfile(ROOT/'app/src/main/AndroidManifest.xml',A/'AndroidManifest.xml');shutil.copytree(ROOT/'app/src/main/res',A/'res')
shutil.copytree(ROOT/'app/src/main/jniLibs',A/'lib')
shutil.copytree(ROOT/'app/src/main/assets',A/'assets')
(A/'apktool.yml').write_text('version: 3.0.3\napkFileName: CloudTune.apk\nusesFramework:\n  ids:\n  - 1\nsdkInfo:\n  minSdkVersion: 23\n  targetSdkVersion: 28\nversionInfo:\n  versionCode: 27\n  versionName: 1.0.26-fx-test\nresourcesInfo:\n  packageId: 127\ndoNotCompress:\n- arsc\n')
run('java','-jar',TOOLS/'apktool.jar','b',A,'-o',B/'CloudTune-unsigned.apk')
SIGNED=B/'signed'
run('java','-jar',TOOLS/'signer.jar','--apks',B/'CloudTune-unsigned.apk','--out',SIGNED)
found=list(SIGNED.glob('*debugSigned.apk'))
if len(found)!=1:raise RuntimeError('APK signing failed: no signed output')
shutil.copyfile(found[0],DIST/'CloudTune-1.0.26-fx-test.apk')
testcp=os.pathsep.join(map(str,[C,TOOLS/'json.jar',SDK]))
run('java','-jar',TOOLS/'ecj.jar','-source','1.8','-target','1.8','-encoding','UTF-8','-cp',testcp,'-d',B/'tests',*sorted((ROOT/'tests').glob('*.java')))
run('java','-cp',str(B/'tests')+os.pathsep+testcp,'com.cloudtune.player.ProtocolTests')
run('java','-cp',str(B/'tests')+os.pathsep+testcp,'com.cloudtune.player.ConvolverTests',ROOT/'app/src/main/assets/soundeffect/viper_blackdiamond_headset.irs')
run('java','-cp',str(B/'tests')+os.pathsep+testcp,'com.cloudtune.player.PortraitLookupTests',ROOT/'tests/fixtures')
run('java','-cp',str(B/'tests')+os.pathsep+testcp,'com.cloudtune.player.UploadTests')
print('Built:',DIST/'CloudTune-1.0.26-fx-test.apk')
