package io.github.icassius.pixelvcrbt;

import android.Manifest;
import android.content.*;
import android.os.*;
import android.telephony.*;
import android.telephony.ims.ImsManager;
import java.util.*;
import java.util.function.Supplier;

/** Live nonce-bound query; IMS has a separate bounded, coalesced request. */
final class RuntimeProbe {
    static final String UI="io.github.icassius.pixelvcrbt", DIALER="com.google.android.dialer";
    static final String QUERY=UI+".QUERY_RUNTIME", REPLY=UI+".RUNTIME_REPLY";
    private final String instance=UUID.randomUUID().toString();
    private final Context host;
    private final Handler main;
    private final Supplier<Bundle> snapshot;
    private long sequence;
    private final ImsQueries queries=new ImsQueries();
    private static RuntimeProbe installed;
    private final java.util.concurrent.ExecutorService historyWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final String environment;
    private String recorded="";
    private RuntimeProbe(Context host,Handler main,Supplier<Bundle> snapshot){this.host=host;this.main=main;this.snapshot=snapshot;environment=RuntimeHistory.environment(host);}
    static synchronized void install(Context host,Handler main,Supplier<Bundle> snapshot){
        if(installed!=null)return;
        RuntimeProbe probe=new RuntimeProbe(host,main,snapshot);
        host.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent request){
            if(!QUERY.equals(request.getAction()))return;
            String nonce=request.getStringExtra("nonce");
            if(!RuntimeReplyGate.validNonce(nonce))return;
            probe.query(nonce);
        }},new IntentFilter(QUERY),Manifest.permission.READ_PHONE_STATE,main,Context.RECEIVER_EXPORTED);
        installed=probe;
    }
    static void changed(){if(installed!=null)installed.main.post(()->installed.record(installed.identity(installed.snapshot.get())));}
    private Bundle identity(Bundle result){
        result.putString("instance",instance);result.putString("module_name",BuildIdentity.NAME);result.putLong("module_code",BuildIdentity.CODE);
        result.putString("module_build",BuildIdentity.SOURCE);result.putString("environment",environment);result.putInt("boot",RuntimeHistory.boot(host));return result;
    }
    private void record(Bundle result){
        Bundle receipt=RuntimeHistory.receipt(result);String content=RuntimeHistory.content(receipt);
        if(content.equals(recorded))return;recorded=content;
        historyWorker.execute(()->{
            try{Bundle response=host.getContentResolver().call(RuntimeHistory.URI,"record",null,receipt);
                if(response==null||!response.getBoolean("saved"))main.post(()->{if(recorded.equals(content))recorded="";});
            }catch(Throwable unavailable){main.post(()->{if(recorded.equals(content))recorded="";});}
        });
    }
    private void query(String nonce){
        // Entry health must not wait for an IMS service which may never call back.
        send(nonce,-1,true,-1);
        try{
            SubscriptionInfo target=null;
            List<SubscriptionInfo> list=host.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(list!=null)for(SubscriptionInfo info:list)if(info.getCarrierId()==2237&&"460".equals(info.getMccString())&&"11".equals(info.getMncString())){
                if(target!=null){send(nonce,-1,false,-1);return;}target=info;
            }
            if(target==null){send(nonce,-1,false,-1);return;}
            int sid=target.getSubscriptionId();
            ImsQueries.Ticket current=queries.current();
            if(current!=null&&!current.matches(sid,SystemClock.elapsedRealtime()))finish(current,-1);
            if(queries.current()!=null){
                if(!queries.join(nonce))send(nonce,-1,false,sid);
                return;
            }
            ImsQueries.Ticket request=queries.begin(sid,nonce,SystemClock.elapsedRealtime());
            main.postDelayed(()->finish(request,-1),ImsQueries.TIMEOUT);
            try{
                host.getSystemService(ImsManager.class).getImsMmTelManager(sid).getRegistrationState(host.getMainExecutor(),state->{
                    finish(request,state);
                });
            }catch(Throwable failed){finish(request,-1);}
        }catch(Throwable failed){send(nonce,-1,false,-1);}
    }
    private void finish(ImsQueries.Ticket request,int state){
        ImsQueries.Completion result=queries.finish(request,state,SystemClock.elapsedRealtime());
        if(result==null)return;
        for(String nonce:result.nonces)send(nonce,result.state,false,request.sid);
    }
    private void send(String nonce,int ims,boolean waiting,int sid){
        Bundle result=identity(snapshot.get());record(result);
        result.putInt("schema",RuntimeReplyGate.SCHEMA);result.putString("instance",instance);result.putLong("sequence",++sequence);
        result.putString("module_name",BuildIdentity.NAME);result.putLong("module_code",BuildIdentity.CODE);result.putString("module_build",BuildIdentity.SOURCE);
        result.putInt("ims",ims);result.putBoolean("ims_pending",waiting);
        result.putBoolean("fallback_attempted",CarrierFallback.attempted());
        host.sendBroadcast(new Intent(REPLY).setPackage(UI).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("nonce",nonce).putExtra("result",result));
    }
}
