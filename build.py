import os
import shutil
import subprocess
import sys
import zipfile
import hashlib
import urllib.request

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
SDK_DIR = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not SDK_DIR: raise RuntimeError('Set ANDROID_HOME to the Android SDK directory')
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, 'build-tools', os.environ.get('BUILD_TOOLS_VERSION','35.0.0'))
PLATFORM_JAR = os.path.join(SDK_DIR, 'platforms', os.environ.get('ANDROID_PLATFORM','android-36.1'), 'android.jar')

WIN = os.name == 'nt'
AAPT2 = os.path.join(BUILD_TOOLS_DIR, 'aapt2.exe' if WIN else 'aapt2')
D8 = os.path.join(BUILD_TOOLS_DIR, 'd8.bat' if WIN else 'd8')
ZIPALIGN = os.path.join(BUILD_TOOLS_DIR, 'zipalign.exe' if WIN else 'zipalign')
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, 'apksigner.bat' if WIN else 'apksigner')

JAVA_HOME = os.environ.get('JAVA_HOME')
if not JAVA_HOME: raise RuntimeError('Set JAVA_HOME to JDK 21')
JAVAC = os.path.join(JAVA_HOME, 'bin', 'javac.exe' if WIN else 'javac')
KEYTOOL = os.path.join(JAVA_HOME, 'bin', 'keytool.exe' if WIN else 'keytool')

BUILD_DIR = os.path.join(PROJECT_DIR, "build")
MODULE_CLASSES_DIR = os.path.join(BUILD_DIR, "module_classes")
RES_COMPILED_DIR = os.path.join(BUILD_DIR, "res_compiled")
KEYSTORE_PATH = os.environ.get('RELEASE_KEYSTORE',os.path.join(BUILD_DIR,'debug.keystore'))

def run_cmd(cmd, cwd=PROJECT_DIR):
    print("Running:", " ".join(cmd) if isinstance(cmd, list) else cmd)
    res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if res.returncode != 0:
        print("STDOUT:\n", res.stdout)
        print("STDERR:\n", res.stderr)
        raise RuntimeError(f"Command failed with code {res.returncode}")
    return res.stdout

def clean_build():
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(BUILD_DIR, exist_ok=True)
    os.makedirs(MODULE_CLASSES_DIR, exist_ok=True)
    os.makedirs(RES_COMPILED_DIR, exist_ok=True)

def ensure_keystore():
    if os.environ.get('RELEASE_KEYSTORE') and not os.path.exists(KEYSTORE_PATH):
        raise RuntimeError('Release keystore missing')
    if not os.path.exists(KEYSTORE_PATH):
        print("[1/6] Generating debug keystore...")
        cmd = [
            KEYTOOL, "-genkeypair",
            "-keystore", KEYSTORE_PATH,
            "-storepass", "android",
            "-keypass", "android",
            "-alias", "androiddebugkey",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=Android Debug,O=Android,C=US"
        ]
        run_cmd(cmd)

def compile_java():
    print("[2/6] Resolving official libxposed API 102 (compile only)...")
    url='https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar'
    data=urllib.request.urlopen(url,timeout=30).read()
    if hashlib.sha256(data).hexdigest()!='423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0':
        raise RuntimeError('libxposed API checksum mismatch')
    aar=os.path.join(BUILD_DIR,'libxposed-api.aar')
    with open(aar,'wb') as out:out.write(data)
    api_jar=os.path.join(BUILD_DIR,'libxposed-api.jar')
    with zipfile.ZipFile(aar) as archive,open(api_jar,'wb') as out:out.write(archive.read('classes.jar'))
    print("[3/6] Compiling module classes...")
    module_src = []
    for root, _, files in os.walk(os.path.join(PROJECT_DIR, "src", "main", "java")):
        module_src.extend(os.path.join(root, f) for f in sorted(files) if f.endswith(".java"))
    classpath = os.pathsep.join([PLATFORM_JAR, api_jar])
    cmd_module = [
        JAVAC, "-encoding", "UTF-8", "-source", "8", "-target", "8",
        "-cp", classpath,
        "-d", MODULE_CLASSES_DIR
    ] + module_src
    run_cmd(cmd_module)

def dex_classes():
    print("[4/6] Creating DEX with d8...")
    class_files = []
    for root, _, files in os.walk(MODULE_CLASSES_DIR):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    cmd_d8 = [
        D8,
        "--min-api", "28",
        "--output", BUILD_DIR,
        "--lib", PLATFORM_JAR, "--classpath", os.path.join(BUILD_DIR,"libxposed-api.jar")
    ] + class_files
    run_cmd(cmd_d8)

def package_and_sign():
    print("[5/6] Compiling resources and linking APK with aapt2...")
    res_dir = os.path.join(PROJECT_DIR, "src", "main", "res")
    manifest = os.path.join(PROJECT_DIR, "src", "main", "AndroidManifest.xml")

    # aapt2 compile
    cmd_compile_res = [
        AAPT2, "compile",
        "--dir", res_dir,
        "-o", os.path.join(BUILD_DIR, "resources.zip")
    ]
    run_cmd(cmd_compile_res)

    # aapt2 link
    unaligned_apk = os.path.join(BUILD_DIR, "unaligned.apk")
    cmd_link = [
        AAPT2, "link",
        "-I", PLATFORM_JAR,
        "--min-sdk-version", "28",
        "--target-sdk-version", "35",
        "--manifest", manifest,
        "-o", unaligned_apk,
        os.path.join(BUILD_DIR, "resources.zip")
    ]
    run_cmd(cmd_link)

    # Add classes.dex to APK
    dex_path = os.path.join(BUILD_DIR, "classes.dex")
    with zipfile.ZipFile(unaligned_apk, 'a') as apk_zip:
        apk_zip.write(dex_path, "classes.dex")
        resources=os.path.join(PROJECT_DIR,"src","main","resources")
        for root,_,files in os.walk(resources):
            for name in sorted(files):
                path=os.path.join(root,name)
                apk_zip.write(path,os.path.relpath(path,resources).replace(os.sep,'/'))

    print("[6/6] Aligning and signing APK...")
    aligned_apk = os.path.join(BUILD_DIR, "aligned.apk")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    cmd_zipalign = [
        ZIPALIGN, "-p", "-f", "4",
        unaligned_apk, aligned_apk
    ]
    run_cmd(cmd_zipalign)

    final_apk = os.path.join(PROJECT_DIR, "PixelVcrbt.apk")
    if os.path.exists(final_apk):
        os.remove(final_apk)
    shutil.copy2(aligned_apk, final_apk)

    os.environ.setdefault('KS_PASS','android')
    os.environ.setdefault('KEY_PASS',os.environ['KS_PASS'])
    cmd_sign = [
        APKSIGNER, "sign",
        "--ks", KEYSTORE_PATH,
        "--ks-key-alias", os.environ.get('KEY_ALIAS','androiddebugkey'),
        "--ks-pass", "env:KS_PASS",
        "--key-pass", "env:KEY_PASS",
        final_apk
    ]
    run_cmd(cmd_sign)

    # Verify signature
    cmd_verify = [APKSIGNER, "verify", "--verbose", final_apk]
    run_cmd(cmd_verify)
    print(f"\n[SUCCESS] Built signed module APK: {final_apk}")
    print(f"Size: {os.path.getsize(final_apk)} bytes")

if __name__ == "__main__":
    clean_build()
    ensure_keystore()
    compile_java()
    dex_classes()
    package_and_sign()
