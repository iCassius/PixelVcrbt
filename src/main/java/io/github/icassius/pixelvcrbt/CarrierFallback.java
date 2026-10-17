package io.github.icassius.pixelvcrbt;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional fallback executes only under privileged Dialer UID, never the module UI or system scope. */
final class CarrierFallback {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final String TAG="PixelVcrbtFallback";
    private static volatile String lastState="not_requested";
    private static volatile boolean capability;
    private static volatile boolean attempted;
    static String state(){return lastState;}
    static boolean capable(){return capability;}
    static boolean attempted(){return attempted;}
    private static String observed(Map<String,Object> values){
        StringBuilder state=new StringBuilder();
        for(String key:FallbackPlan.KEYS)state.append(!values.containsKey(key)?'m':Boolean.TRUE.equals(values.get(key))?'1':'0');
        return state.toString();
    }
    private static boolean save(SharedPreferences prefs,String key,FallbackRecovery record){
        return prefs.edit().putString(key+".phase",record.phase).putInt(key+".count",record.attempts)
            .putLong(key+".at",record.at).putString(key+".before",record.before).commit();
    }
    static void schedule(Context context){
        if(!busy.compareAndSet(false,true))return;
        worker.execute(()->{try{apply(context);}catch(Throwable t){lastState="failed:"+t.getClass().getSimpleName();android.util.Log.w(TAG,"fallback_failed "+t.getClass().getSimpleName());}finally{busy.set(false);}});
    }
    private static boolean flag(String key,String def){try{return "1".equals(Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,def));}catch(Throwable t){return false;}}
    private static boolean loaded(SubscriptionInfo target)throws Exception{
        String value=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"gsm.sim.state","");
        String[] states=value.split(",",-1);int slot=target.getSimSlotIndex();return slot>=0&&slot<states.length&&"LOADED".equals(states[slot]);
    }
    private static boolean idle(Context c){
        TelecomManager telecom=c.getSystemService(TelecomManager.class);
        TelephonyManager phone=c.getSystemService(TelephonyManager.class);
        return telecom!=null&&phone!=null&&!telecom.isInCall()&&phone.getCallState()==TelephonyManager.CALL_STATE_IDLE;
    }
    private static void apply(Context c)throws Exception{
        capability=false;attempted=false;lastState="probing";
        if(!"com.google.android.dialer".equals(c.getPackageName())){lastState="wrong_host";return;}
        if(c.checkSelfPermission(Manifest.permission.MODIFY_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){lastState="permission_missing";return;}
        // These vendor keys belong to the Shannon implementation, not every privileged Dialer.
        ServiceInfo vendor=c.getPackageManager().getServiceInfo(new ComponentName("com.shannon.imsservice","com.shannon.imsservice.ShannonImsService"),PackageManager.GET_META_DATA);
        if(!vendor.enabled||!vendor.applicationInfo.enabled||
            (vendor.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0||
            !"android.permission.BIND_IMS_SERVICE".equals(vendor.permission)||vendor.metaData==null||
            !vendor.metaData.getBoolean("android.telephony.ims.MMTEL_FEATURE",false)){
            lastState="vendor_service_not_identified";return;
        }
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"carrier_config");
        if(!(binder instanceof IBinder)){lastState="service_unavailable";return;}
        Object loader=Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        if(loader==null){lastState="service_unavailable";return;}
        java.lang.reflect.Method override=FeatureContract.instanceMethod(loader.getClass(),"overrideConfig",void.class,int.class,PersistableBundle.class,boolean.class);
        capability=true;
        if(flag("persist.sys.vcrbt.disabled","0")||!flag("persist.sys.vcrbt.carrier_fallback","1")||new java.io.File(c.getFilesDir(),"vcrbt_disabled").exists()){lastState="disabled";return;}
        if(!idle(c)){lastState="call_active";return;}
        SubscriptionManager sm=c.getSystemService(SubscriptionManager.class);SubscriptionInfo target=null;
        List<SubscriptionInfo> list=sm.getActiveSubscriptionInfoList();
        if(list!=null)for(SubscriptionInfo i:list)if(i.getCarrierId()==2237&&"460".equals(i.getMccString())&&"11".equals(i.getMncString())){
            if(target!=null){lastState="ambiguous_subscription";return;}target=i;
        }
        if(target==null||!loaded(target)){lastState="subscription_not_ready";return;}
        final int sid=target.getSubscriptionId();CarrierConfigManager cm=c.getSystemService(CarrierConfigManager.class);
        PersistableBundle current=cm.getConfigForSubId(sid,FallbackPlan.KEYS);
        if(!CarrierConfigManager.isConfigForIdentifiedCarrier(current)){lastState="config_unavailable";return;}
        Map<String,Object> values=new HashMap<>();for(String key:FallbackPlan.KEYS)if(current.containsKey(key)){
            Object value=current.get(key);if(!(value instanceof Boolean)){lastState="config_type_mismatch";return;}values.put(key,value);
        }
        Map<String,Boolean> missing=FallbackPlan.missing(values);
        int boot=Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);
        if(boot<0){lastState="boot_unknown";return;}
        SharedPreferences attempts=c.getSharedPreferences("pixel_vcrbt_fallback",0);String token=boot+":"+sid;
        // Card ID is a local discriminator, never included in status or logs. Do not reset the budget on module updates.
        String journalKey="journal2:"+token+":"+target.getCardId();
        FallbackRecovery record=new FallbackRecovery(attempts.getString(journalKey+".phase","none"),attempts.getInt(journalKey+".count",0),
            attempts.getLong(journalKey+".at",0),attempts.getString(journalKey+".before",""));
        attempted=record.attempts>0||attempts.getBoolean(token,false);
        if(missing.isEmpty()){
            lastState=record.attempts>0?(save(attempts,journalKey,record.phase("verified"))?"verified":"verified_record_failed"):"provided";
            return;
        }
        String decision=record.decision(observed(values),SystemClock.elapsedRealtime(),attempts.getBoolean(token,false));
        if(!"write".equals(decision)){
            lastState=decision;
            if("retry_wait".equals(decision))new Handler(Looper.getMainLooper()).postDelayed(()->schedule(c),FallbackRecovery.RETRY_DELAY);
            return;
        }
        if(!idle(c)){lastState="call_active";return;}
        PersistableBundle patch=new PersistableBundle();for(String key:missing.keySet())patch.putBoolean(key,true);
        record=record.prepare(observed(values),SystemClock.elapsedRealtime());
        if(!save(attempts,journalKey,record)){lastState="attempt_record_failed";return;}
        attempted=true;
        // Persist uncertainty before crossing the system Binder boundary. Readback always wins on restart.
        record=record.phase("submitted");
        if(!save(attempts,journalKey,record)){lastState="submit_record_failed";return;}
        if(!idle(c)){lastState="call_active";new Handler(Looper.getMainLooper()).postDelayed(()->schedule(c),FallbackRecovery.RETRY_DELAY);return;}
        lastState="requested";
        try{override.invoke(loader,sid,patch,true);}catch(Throwable failed){
            save(attempts,journalKey,record.phase("uncertain"));lastState="write_uncertain";
            new Handler(Looper.getMainLooper()).postDelayed(()->schedule(c),FallbackRecovery.RETRY_DELAY);
            return;
        }
        android.util.Log.i(TAG,"fallback_requested uid="+android.os.Process.myUid()+" subId="+sid+" keys="+missing.keySet());
        long until=SystemClock.elapsedRealtime()+12000;
        do{
            PersistableBundle actual=cm.getConfigForSubId(sid,FallbackPlan.KEYS);boolean equal=CarrierConfigManager.isConfigForIdentifiedCarrier(actual);
            for(String key:missing.keySet())equal&=actual.containsKey(key)&&actual.getBoolean(key,false);
            if(equal){lastState=save(attempts,journalKey,record.phase("verified"))?"verified":"verified_record_failed";android.util.Log.i(TAG,"fallback_verified keys="+missing.keySet()+" evidence=effective_config_readback");return;}
            SystemClock.sleep(250);
        }while(SystemClock.elapsedRealtime()<until);
        save(attempts,journalKey,record.phase("uncertain"));
        lastState="readback_failed";android.util.Log.i(TAG,"needs_other_module reason=readback_failed");
        new Handler(Looper.getMainLooper()).postDelayed(()->schedule(c),FallbackRecovery.RETRY_DELAY);
    }
}
