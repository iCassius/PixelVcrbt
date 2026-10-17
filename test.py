import os, pathlib, subprocess, sys
root=pathlib.Path(__file__).parent.resolve()
out=root/'build'/'tests';out.mkdir(parents=True,exist_ok=True)
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
suffix='.exe' if os.name=='nt' else ''
src=root/'src/main/java/io/github/icassius/pixelvcrbt'
names=['SessionGate','EarlyMediaPolicy','FallbackPlan','FallbackRecovery','HistoryPolicy','DetectionStatus','ModuleVersion','FeatureContract','HookInstallation','HookRegistry','RuntimeReplyGate','ImsQueries','AudioRestoreAttempt','DiagnosticState','SwitchValue','EndpointSelection']
subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-d',str(out)]+[str(src/(n+'.java')) for n in names]+[str(root/'tests'/(n+'Test.java')) for n in names if n!='SwitchValue'],check=True)
for name in names:
    if name!='SwitchValue':subprocess.run([str(java/('java'+suffix)),'-cp',str(out),'io.github.icassius.pixelvcrbt.'+name+'Test'],check=True)
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:raise RuntimeError('Set ANDROID_HOME for Android API contract tests')
platform=pathlib.Path(sdk)/'platforms'/os.environ.get('ANDROID_PLATFORM','android-36.1')/'android.jar'
classpath=os.pathsep.join([str(out),str(platform)])
android_tests=['VideoCallFeatures','AudioFeatures']
subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-cp',classpath,'-d',str(out)]+[str(src/(n+'.java')) for n in android_tests]+[str(root/'tests'/(n+'Test.java')) for n in android_tests],check=True)
for name in android_tests:subprocess.run([str(java/('java'+suffix)),'-cp',classpath,'io.github.icassius.pixelvcrbt.'+name+'Test'],check=True)
subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-cp',str(platform),'-d',str(out/'android'),str(root/'tests/android/HistoryInstrumentation.java')],check=True)
subprocess.run([sys.executable,str(root/'tests/test_update_feed.py')],check=True)

subprocess.run([sys.executable,str(root/'tests/test_modern_package.py')],check=True)
