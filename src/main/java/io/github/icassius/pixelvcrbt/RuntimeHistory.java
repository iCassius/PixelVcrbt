package io.github.icassius.pixelvcrbt;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** One private, bounded receipt owned by the module app. No live objects or subscriber data. */
final class RuntimeHistory {
    static final android.net.Uri URI=android.net.Uri.parse("content://"+RuntimeProbe.UI+".history");
    private static final String[] STRINGS={"environment","module_build","module_name","instance","phase","init","runtime_fault","adapter"};
    static int boot(Context context){try{return Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);}catch(Throwable ignored){return -1;}}
    static String environment(Context context){
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(RuntimeProbe.DIALER,PackageManager.GET_SIGNING_CERTIFICATES);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            StringBuilder identity=new StringBuilder(Build.FINGERPRINT).append('|').append(info.getLongVersionCode()).append('|').append(info.lastUpdateTime);
            List<String> paths=new ArrayList<>();paths.add(info.applicationInfo.sourceDir);
            if(info.applicationInfo.splitSourceDirs!=null)Collections.addAll(paths,info.applicationInfo.splitSourceDirs);
            Collections.sort(paths);
            for(String path:paths){File file=new File(path);identity.append('|').append(path).append(':').append(file.length()).append(':').append(file.lastModified());}
            digest.update(identity.toString().getBytes(StandardCharsets.UTF_8));
            if(info.signingInfo==null)return "";
            for(Signature signature:info.signingInfo.getApkContentsSigners())digest.update(signature.toByteArray());
            StringBuilder hex=new StringBuilder();for(byte part:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",part&255));return hex.toString();
        }catch(Throwable unavailable){return "";}
    }
    static Bundle receipt(Bundle runtime){
        Bundle result=new Bundle();result.putInt("history_schema",1);
        for(String name:STRINGS){String value=runtime.getString(name,"");result.putString(name,value.substring(0,Math.min(value.length(),name.equals("diagnostics")?4096:256)));}
        result.putInt("boot",runtime.getInt("boot",-1));result.putInt("framework_api",runtime.getInt("framework_api",-1));
        result.putLong("module_code",runtime.getLong("module_code",-1));
        for(String name:new String[]{"hook","features_ready","audio_ready"})result.putBoolean(name,runtime.getBoolean(name));
        return result;
    }
    static String content(Bundle receipt){
        try{
            JSONObject json=new JSONObject();List<String> keys=new ArrayList<>(receipt.keySet());Collections.sort(keys);
            for(String key:keys)json.put(key,receipt.get(key));return json.toString();
        }catch(Exception invalid){return "";}
    }
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getNoBackupFilesDir(),"runtime-history.json"));}
    static synchronized boolean save(Context context,Bundle incoming){
        if(incoming==null)return false;
        Bundle receipt=receipt(incoming);
        if(receipt.getString("environment","").isEmpty()||receipt.getString("module_build","").isEmpty()||
            receipt.getString("instance","").isEmpty())return false;
        String next=content(receipt);if(next.isEmpty())return false;
        Bundle old=read(context);if(next.equals(old.getString("content")))return true;
        AtomicFile target=file(context);FileOutputStream stream=null;
        try{
            JSONObject json=new JSONObject(next);json.put("observed_at",System.currentTimeMillis());
            stream=target.startWrite();stream.write(json.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);return true;
        }catch(Throwable failure){if(stream!=null)target.failWrite(stream);return false;}
    }
    static synchronized Bundle read(Context context){
        Bundle result=new Bundle();
        try{
            byte[] data=file(context).readFully();if(data.length>16384)return result;
            JSONObject json=new JSONObject(new String(data,StandardCharsets.UTF_8));if(json.optInt("history_schema")!=1)return result;
            for(String name:STRINGS)result.putString(name,json.optString(name,""));
            result.putInt("boot",json.optInt("boot",-1));result.putInt("framework_api",json.optInt("framework_api",-1));
            result.putLong("module_code",json.optLong("module_code",-1));result.putLong("observed_at",json.optLong("observed_at",0));
            for(String name:new String[]{"hook","features_ready","audio_ready"})result.putBoolean(name,json.optBoolean(name));
            result.putString("content",content(receipt(result)));
        }catch(Throwable missingOrDamaged){}
        return result;
    }
}
