package com.antigravity.pixelvcrbt;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
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
    static void schedule(Context context){
        if(!busy.compareAndSet(false,true))return;
        worker.execute(()->{try{apply(context);}catch(Throwable t){android.util.Log.w(TAG,"fallback_failed "+t.getClass().getSimpleName());}finally{busy.set(false);}});
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
        if(!"com.google.android.dialer".equals(c.getPackageName())||!"mustang".equals(Build.DEVICE)||!"CP3A.260905.009".equals(Build.DISPLAY)||
            c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()!=20148463L)return;
        if(flag("persist.sys.vcrbt.disabled","0")||!flag("persist.sys.vcrbt.carrier_fallback","1"))return;
        if(c.checkSelfPermission(Manifest.permission.MODIFY_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){android.util.Log.i(TAG,"needs_other_module reason=permission");return;}
        if(!idle(c)){android.util.Log.i(TAG,"fallback_deferred reason=call_active");return;}
        SubscriptionManager sm=c.getSystemService(SubscriptionManager.class);SubscriptionInfo target=null;
        List<SubscriptionInfo> list=sm.getActiveSubscriptionInfoList();
        if(list!=null)for(SubscriptionInfo i:list)if(i.getCarrierId()==2237&&"460".equals(i.getMccString())&&"11".equals(i.getMncString())){
            if(target!=null)return;target=i;
        }
        if(target==null||!loaded(target))return;
        final int sid=target.getSubscriptionId();CarrierConfigManager cm=c.getSystemService(CarrierConfigManager.class);
        PersistableBundle current=cm.getConfigForSubId(sid,FallbackPlan.KEYS);
        if(!CarrierConfigManager.isConfigForIdentifiedCarrier(current))return;
        Map<String,Object> values=new HashMap<>();for(String key:FallbackPlan.KEYS)if(current.containsKey(key))values.put(key,current.get(key));
        Map<String,Boolean> missing=FallbackPlan.missing(values);
        if(missing.isEmpty()){android.util.Log.i(TAG,"fallback_noop all_prerequisites_provided=true uid="+android.os.Process.myUid());return;}
        int boot=Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);
        SharedPreferences attempts=c.getSharedPreferences("pixel_vcrbt_fallback",0);String token=boot+":"+sid;
        if(attempts.getBoolean(token,false)){android.util.Log.i(TAG,"needs_other_module reason=already_attempted_or_external_reset");return;}
        if(!idle(c))return;
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"carrier_config");
        if(binder==null)return;
        Object loader=Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        PersistableBundle patch=new PersistableBundle();for(String key:missing.keySet())patch.putBoolean(key,true);
        // One attempt per boot/subscription prevents competing with a different module's policy.
        attempts.edit().putBoolean(token,true).commit();
        loader.getClass().getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,sid,patch,true);
        android.util.Log.i(TAG,"fallback_requested uid="+android.os.Process.myUid()+" subId="+sid+" keys="+missing.keySet());
        long until=SystemClock.elapsedRealtime()+12000;
        do{
            PersistableBundle actual=cm.getConfigForSubId(sid,FallbackPlan.KEYS);boolean equal=CarrierConfigManager.isConfigForIdentifiedCarrier(actual);
            for(String key:missing.keySet())equal&=actual.containsKey(key)&&actual.getBoolean(key,false);
            if(equal){android.util.Log.i(TAG,"fallback_verified keys="+missing.keySet()+" evidence=effective_config_readback");return;}
            SystemClock.sleep(250);
        }while(SystemClock.elapsedRealtime()<until);
        android.util.Log.i(TAG,"needs_other_module reason=readback_failed");
    }
}
