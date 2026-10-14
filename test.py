import os, pathlib, subprocess
root=pathlib.Path(__file__).parent.resolve()
out=root/'build'/'tests';out.mkdir(parents=True,exist_ok=True)
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
suffix='.exe' if os.name=='nt' else ''
src=root/'src/main/java/com/antigravity/pixelvcrbt'
names=['SessionGate','EarlyMediaPolicy','FallbackPlan','RuntimeReason']
subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-d',str(out)]+[str(src/(n+'.java')) for n in names]+[str(p) for p in sorted((root/'tests').glob('*Test.java'))],check=True)
for name in names:
    subprocess.run([str(java/('java'+suffix)),'-cp',str(out),'com.antigravity.pixelvcrbt.'+name+'Test'],check=True)
