package io.github.icassius.pixelvcrbt;

import android.Manifest;
import android.content.*;
import android.os.*;
import android.telephony.*;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsManager;
import java.util.function.Supplier;

/** Live nonce-bound query. No persisted result, Root files or Dialer launch. */
final class RuntimeProbe {
    static final String UI="io.github.icassius.pixelvcrbt", DIALER="com.google.android.dialer";
    static final String QUERY=UI+".QUERY_RUNTIME", REPLY=UI+".RUNTIME_REPLY";
    static void install(Context host,Handler main,Supplier<Bundle> snapshot){
        host.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent request){
            String nonce=request.getStringExtra("nonce");if(nonce==null||nonce.length()!=36)return;
            Bundle result=snapshot.get();
            result.putBoolean("ims_pending",true);
            send(host,nonce,result);
            try{
                SubscriptionInfo target=null;
                java.util.List<SubscriptionInfo> list=host.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
                if(list!=null)for(SubscriptionInfo i:list)if(i.getCarrierId()==2237&&"460".equals(i.getMccString())&&"11".equals(i.getMncString())){
                    if(target!=null){result.putBoolean("ims_pending",false);send(host,nonce,result);return;}target=i;
                }
                if(target==null){result.putBoolean("ims_pending",false);send(host,nonce,result);return;}
                final int sid=target.getSubscriptionId();
                attempt(host,sid,result);send(host,nonce,result);
                host.getSystemService(ImsManager.class).getImsMmTelManager(sid).getRegistrationState(host.getMainExecutor(),state->{
                    Bundle fresh=snapshot.get();attempt(host,sid,fresh);fresh.putInt("ims",state);send(host,nonce,fresh);
                });
            }catch(Throwable ignored){result.putBoolean("ims_pending",false);send(host,nonce,result);}
        }},new IntentFilter(QUERY),Manifest.permission.READ_PHONE_STATE,main,Context.RECEIVER_EXPORTED);
    }
    private static void attempt(Context host,int sid,Bundle result){
        int boot=android.provider.Settings.Global.getInt(host.getContentResolver(),"boot_count",-1);
        result.putBoolean("fallback_attempted",host.getSharedPreferences("pixel_vcrbt_fallback",0).getBoolean(boot+":"+sid,false));
    }
    private static void send(Context host,String nonce,Bundle result){
        host.sendBroadcast(new Intent(REPLY).setPackage(UI).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("nonce",nonce).putExtra("result",result));
    }
}
