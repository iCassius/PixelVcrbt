import os, pathlib, subprocess, sys
root=pathlib.Path(__file__).parent.resolve()
out=root/'build'/'tests';out.mkdir(parents=True,exist_ok=True)
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
suffix='.exe' if os.name=='nt' else ''
src=root/'src/main/java/io/github/icassius/pixelvcrbt'
names=['SessionGate','EarlyMediaPolicy','FallbackPlan','DetectionStatus','ModuleVersion']
subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-d',str(out)]+[str(src/(n+'.java')) for n in names]+[str(p) for p in sorted((root/'tests').glob('*Test.java'))],check=True)
for name in names:
    subprocess.run([str(java/('java'+suffix)),'-cp',str(out),'io.github.icassius.pixelvcrbt.'+name+'Test'],check=True)
subprocess.run([sys.executable,str(root/'tests/test_update_feed.py')],check=True)
