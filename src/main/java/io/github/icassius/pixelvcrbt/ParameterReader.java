package io.github.icassius.pixelvcrbt;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import android.telephony.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ParameterReader {
    // These three names are module-owned feature settings, not Android CarrierConfig keys.
    static final String[][] MODULE_FIELDS={
        {"vcrbt_surface_bridge_enabled_bool","显示语音起呼的视频彩铃"},
        {"vcrbt_audio_preserve_enabled_bool","保持听筒并保留用户音频选择"},
        {"vcrbt_carrier_fallback_enabled_bool","Dialer 加载后补齐缺失前置配置"}
    };
    static final String[][] PROVIDER_FIELDS={
        {"carrier_vt_available_bool","允许运营商视频能力"},
        {"imsvt.video_on_default_bearer_supported_bool","允许视频早期媒体使用默认承载"},
        {"vendor.ims.carrier_video_over_lte_bool","允许 LTE 视频传输"},
        {"vendor.ims.carrier_video_over_wifi_bool","允许 VoWiFi 视频传输"}
    };
    static final class Result {final Map<String,Object> values=new HashMap<>();boolean fallbackAvailable,compatible,configReady,targetFound;}
    static String state(Object value){return value instanceof Boolean?((Boolean)value?"已启用":"已禁用"):"未检测到";}
    static Result read(Context context){
        Result result=new Result();int sid=-1;
        boolean compatible=false,modify=false;
        try{
            compatible="mustang".equals(Build.DEVICE)&&"CP3A.260905.009".equals(Build.DISPLAY)&&context.getPackageManager().getPackageInfo("com.google.android.dialer",0).getLongVersionCode()==20148463L;
            modify=context.getPackageManager().checkPermission(Manifest.permission.MODIFY_PHONE_STATE,"com.google.android.dialer")==PackageManager.PERMISSION_GRANTED;
        }catch(Exception ignored){}
        Boolean disabled=property("persist.sys.vcrbt.disabled",false),audio=property("persist.sys.vcrbt.audio_preserve",true),fallback=property("persist.sys.vcrbt.carrier_fallback",true);
        result.compatible=compatible;
        result.values.put(MODULE_FIELDS[0][0],disabled==null?null:!disabled);
        result.values.put(MODULE_FIELDS[1][0],audio);
        result.fallbackAvailable=compatible&&modify&&Boolean.FALSE.equals(disabled)&&Boolean.TRUE.equals(fallback);
        result.values.put(MODULE_FIELDS[2][0],fallback);
        try{
            if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED){
                List<SubscriptionInfo> list=context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
                if(list!=null)for(SubscriptionInfo i:list)if(i.getCarrierId()==2237&&"460".equals(i.getMccString())&&"11".equals(i.getMncString())){
                    if(sid!=-1){sid=-1;break;}sid=i.getSubscriptionId();
                }
                if(sid!=-1){
                    result.targetFound=true;
                    PersistableBundle b=context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sid,FallbackPlan.KEYS);
                    if(CarrierConfigManager.isConfigForIdentifiedCarrier(b)){result.configReady=true;for(String key:FallbackPlan.KEYS)if(b.containsKey(key))result.values.put(key,b.get(key));}
                }
            }
        }catch(Throwable t){android.util.Log.w("PixelVcrbtStatus","Direct read unavailable: "+t.getClass().getSimpleName());}
        android.util.Log.i("PixelVcrbtStatus","direct_read subId="+sid+" provider_keys="+(result.values.size()-3)+" fallback_available="+result.fallbackAvailable+" dialer_dependency=false");
        return result;
    }
    private static Boolean property(String key,boolean def){
        java.lang.Process process=null;
        try{
            process=new ProcessBuilder("/system/bin/getprop",key).start();
            if(process.waitFor(2,TimeUnit.SECONDS)&&process.exitValue()==0){
                String value=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
                if(value.isEmpty())return def;if("0".equals(value)||"1".equals(value))return "1".equals(value);
            }
        }catch(Throwable ignored){}finally{if(process!=null)process.destroy();}
        return null;
    }
}
