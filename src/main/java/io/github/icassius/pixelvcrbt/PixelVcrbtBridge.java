package io.github.icassius.pixelvcrbt;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.telecom.*;
import android.telephony.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Dialer-only early media renderer and bounded prerequisite fallback. No KSU files or handshake. */
public final class PixelVcrbtBridge extends XposedModule {
    private static final String PKG="com.google.android.dialer", TAG="PixelVcrbtBridge";
    private static final Map<Call,Session> sessions=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final HookRegistry<Method> hooks=new HookRegistry<>();
    private static final java.util.concurrent.atomic.AtomicBoolean entryInstalled=new java.util.concurrent.atomic.AtomicBoolean();
    private static final Set<Class<?>> traced=new HashSet<>();
    private static final Set<Class<?>> textureListeners=new HashSet<>();
    private static Handler main;
    private static Application app;
    private static Activity foreground;
    private static final HookInstallation installation=new HookInstallation();
    private static DialerFeatures features;
    private static boolean audioReady;
    private static boolean endpointReady,legacyAudioReady;
    private static final DiagnosticState diagnostics=new DiagnosticState();
    private static long nextSession;
    private static String audioReason="not_probed",fallbackReason="not_probed";
    private static long dialerVersion=-1;
    private static volatile String runtimeFault="";
    private static Runnable watchdog;
    private static String latestEvent="idle";
    private static String initReason="initializing";
    private static final ThreadLocal<Boolean> changingView=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> changingAudio=new ThreadLocal<>();

    private static void status(String state) {
        latestEvent=state;
        android.util.Log.i(TAG,"VCRBT_STATUS at="+System.currentTimeMillis()+" pid="+android.os.Process.myPid()+" "+state);
    }
    private static boolean enabled() {
        try {
            if(new java.io.File(app.getFilesDir(),"vcrbt_disabled").exists()){
                report(0,DiagnosticState.Step.MODULE_SWITCH,DiagnosticState.State.DISABLED,"local_switch_disabled");return false;
            }
            return readFlag("persist.sys.vcrbt.disabled","0",true,DiagnosticState.Step.MODULE_SWITCH);
        }catch(Throwable t){report(0,DiagnosticState.Step.MODULE_SWITCH,DiagnosticState.State.FAILED,"local_switch_read:"+errorType(t));return false;}
    }
    private static boolean audioPreserveEnabled(){
        return readFlag("persist.sys.vcrbt.audio_preserve","1",false,DiagnosticState.Step.AUDIO_SWITCH);
    }
    private static boolean readFlag(String key,String def,boolean inverted,DiagnosticState.Step step){
        try{
            Method get=Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class);
            Boolean value=SwitchValue.parse((String)get.invoke(null,key,def),inverted);
            if(value==null){report(0,step,DiagnosticState.State.FAILED,"invalid_property_value");return false;}
            report(0,step,value?DiagnosticState.State.READY:DiagnosticState.State.DISABLED,value?"enabled":"user_disabled");return value;
        }catch(Throwable t){report(0,step,DiagnosticState.State.FAILED,"property_read:"+errorType(t));return false;}
    }
    private static String errorType(Throwable t){
        if(t instanceof InvocationTargetException&&t.getCause()!=null)t=t.getCause();
        String name=t.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");return name.substring(0,Math.min(48,name.length()));
    }
    private static void report(long session,DiagnosticState.Step step,DiagnosticState.State state,String reason){
        if(diagnostics.record(session,step,state,reason,SystemClock.elapsedRealtime()))
            android.util.Log.i(TAG,"VCRBT_DIAGNOSTIC step="+step+" state="+state+" reason="+reason);
    }
    private static PixelVcrbtBridge framework;
    private boolean targetProcess;
    @Override public void onModuleLoaded(ModuleLoadedParam p) {
        targetProcess=!p.isSystemServer()&&PKG.equals(p.getProcessName());
        if(targetProcess){framework=this;status("modern_api_loaded api="+getApiVersion());}
    }
    @Override public void onPackageReady(PackageReadyParam p) {
        if(!targetProcess||!PKG.equals(p.getPackageName())||!p.isFirstPackage())return;
        if(!entryInstalled.compareAndSet(false,true))return;
        final ClassLoader loader=p.getClassLoader();
        try {
            rawHook(Instrumentation.class.getDeclaredMethod("callApplicationOnCreate",Application.class),chain->{
                Object result=chain.proceed();
                initialize((Application)chain.getArg(0),loader);
                return result;
            });
            Application current=(Application)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if(current!=null)initialize(current,loader);
        }catch(Throwable t){initReason="entry_failed";status("init_failed error="+t.getClass().getSimpleName());android.util.Log.e(TAG,"Modern entry failure",t);}
    }
    private static HookRegistry<Method>.Lease rawHook(Method method,XposedInterface.Hooker callback) {
        return hooks.acquire(method,()->{
            XposedInterface.HookHandle handle=framework.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(callback);
            return handle::unhook;
        });
    }
    private static HookRegistry<Method>.Lease intercept(Method method,XposedInterface.Hooker callback) {
        HookRegistry<Method>.Lease handle=rawHook(method,chain->{
            if(!installation.ready()||!runtimeFault.isEmpty())return chain.proceed();
            try{return callback.intercept(chain);}catch(Throwable failure){
                runtimeFault=method.getName()+":"+failure.getClass().getSimpleName();
                status("callback_error step="+runtimeFault);
                // Never proceed a second time: the host may already have run before throwing.
                main.post(()->{for(Session session:copy())session.close();sessions.clear();});
                RuntimeProbe.changed();
                throw failure;
            }
        });
        if("installing".equals(installation.phase()))installation.added(method.getDeclaringClass().getName()+"."+method.getName(),handle::unhook);
        return handle;
    }
    // Sessions retain Android objects and Surface ownership: reload only by restarting Dialer.
    // API 102 defaults to rejecting hot reload; no API 102-only parameter types here.
    private static Method declared(Class<?> type,String name,Class<?>...parameters)throws Exception {
        return FeatureContract.instanceMethod(type,name,void.class,parameters);
    }
    private static void initialize(Application context,ClassLoader loader) {
        if(context==null)return;
        if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(()->initialize(context,loader));return;}
        if(!installation.begin())return;
        try {
            if(app==null){app=context;main=new Handler(Looper.getMainLooper());RuntimeProbe.install(context,main,PixelVcrbtBridge::snapshot);}
            if(!PKG.equals(context.getPackageName()))throw new IllegalStateException("unexpected_package");
            try{dialerVersion=context.getPackageManager().getPackageInfo(PKG,0).getLongVersionCode();}catch(Exception ignored){}
            installation.step("dialer_contract");features=DialerFeatures.probe(loader);
            installation.installing();installation.step("view_visibility");
            intercept(declared(View.class,"setVisibility",int.class),chain->{
                if(!Boolean.TRUE.equals(changingView.get()))for(Session s:copy())
                    if(chain.getThisObject()==s.placeholder)s.placeholderOwned=false;
                return chain.proceed();
            });
            installation.step("business_label");
            intercept(declared(TextView.class,"setText",CharSequence.class,TextView.BufferType.class),chain->{
                Object[] args=chain.getArgs().toArray();
                if(!Boolean.TRUE.equals(changingView.get()))for(Session s:copy())
                    if(chain.getThisObject()==s.businessLabel&&s.surfaceSubmitted){s.nativeLabel=(CharSequence)args[0];args[0]="正在语音呼叫 · 视频彩铃";}
                return chain.proceed(args);
            });
            installation.step("call_added");
            intercept(features.added,chain->{
                Object result=chain.proceed();
                Call c=(Call)chain.getArg(0);InCallService owner=(InCallService)chain.getThisObject();main.post(()->add(c,owner));
                return result;
            });
            installation.step("call_removed");
            intercept(features.removed,chain->{
                Call c=(Call)chain.getArg(0);main.post(()->remove(c));
                return chain.proceed();
            });
            installation.step("activity_lifecycle");
            Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){if(installation.ready()&&features.host.isInstance(a)){foreground=a;wakeAll();}}
                public void onActivityPaused(Activity a){if(foreground==a){foreground=null;for(Session s:copy())s.detach();}}
                public void onActivityDestroyed(Activity a){if(foreground==a)foreground=null;for(Session s:copy())if(s.host==a)s.detach();}
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
            };
            context.registerActivityLifecycleCallbacks(lifecycle);
            installation.added("activity_lifecycle",()->context.unregisterActivityLifecycleCallbacks(lifecycle));
            XposedInterface.Hooker audioRequest=chain->{
                if(!Boolean.TRUE.equals(changingAudio.get()))for(Session s:copy())if(s.service==chain.getThisObject()){
                    s.policy.userAudio();s.cancelAudio();status("user_audio_selection_preserved");
                }
                return chain.proceed();
            };
            installation.step("audio_selection");
            List<HookRegistry<Method>.Lease> audioHandles=new ArrayList<>();
            audioReason=features.audio.reason;
            try{
                if(features.audio.safe)for(Method method:features.audio.requests)audioHandles.add(rawHook(method,chain->installation.ready()&&runtimeFault.isEmpty()&&audioReady?audioRequest.intercept(chain):chain.proceed()));
                audioReady=features.audio.safe&&(features.audio.legacy||features.audio.endpoint);
                legacyAudioReady=audioReady&&features.audio.legacy;
                for(HookRegistry<Method>.Lease handle:audioHandles)installation.added("audio_selection",handle::unhook);
            }catch(Throwable optionalFailure){
                audioReady=false;legacyAudioReady=false;audioReason="audio_hook_failed";
                for(HookRegistry<Method>.Lease handle:audioHandles)try{handle.unhook();}catch(Throwable ignored){}
            }
            List<HookRegistry<Method>.Lease> endpointHandles=new ArrayList<>();
            if(audioReady&&features.audio.endpoint)try{
                endpointHandles.add(rawHook(features.audio.endpointCallbacks[0],chain->{
                    Object result=chain.proceed();
                    if(installation.ready()&&runtimeFault.isEmpty()&&endpointReady){
                        CallEndpointRouter.remember((InCallService)chain.getThisObject(),(List<?>)chain.getArg(0));main.post(PixelVcrbtBridge::wakeAll);
                    }
                    return result;
                }));
                endpointHandles.add(rawHook(features.audio.endpointCallbacks[1],chain->{
                    Object result=chain.proceed();if(installation.ready()&&runtimeFault.isEmpty()&&endpointReady)main.post(PixelVcrbtBridge::wakeAll);return result;
                }));
                endpointReady=true;
                for(HookRegistry<Method>.Lease handle:endpointHandles)installation.added("endpoint_observation",handle::unhook);
            }catch(Throwable optionalFailure){
                endpointReady=false;audioReason="endpoint_observation_failed";
                for(HookRegistry<Method>.Lease handle:endpointHandles)try{handle.unhook();}catch(Throwable ignored){}
            }
            audioReady=audioReady&&(endpointReady||legacyAudioReady);
            installation.commit();
            enabled();audioPreserveEnabled();
            status("hooks_ready version="+dialerVersion+" adapter="+DialerFeatures.ADAPTER+" audio="+audioReady+" renderer=native_preferred business_policy=early_media");
            initReason="ready";
            try{
                CarrierFallback.schedule(context);
                BroadcastReceiver carrierReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(installation.ready())CarrierFallback.schedule(app);}};
                context.registerReceiver(carrierReceiver,new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED),null,main,Context.RECEIVER_EXPORTED);
                installation.added("carrier_config_receiver",()->context.unregisterReceiver(carrierReceiver));
                fallbackReason="scheduled";
            }catch(Throwable optionalFailure){fallbackReason="schedule_failed";}

        }catch(Throwable t){initReason=installation.step()+":"+t.getClass().getSimpleName();installation.fail();audioReady=false;endpointReady=false;legacyAudioReady=false;status("init_failed step="+initReason);android.util.Log.e(TAG,"Initialization failure",t);}
        RuntimeProbe.changed();
    }
    private static List<Session> copy(){synchronized(sessions){return new ArrayList<>(sessions.values());}}
    private static Bundle snapshot(){
        Bundle b=new Bundle();b.putBoolean("hook",installation.ready()&&runtimeFault.isEmpty());b.putBoolean("hooks_installed",installation.ready());b.putString("init",initReason);
        b.putInt("framework_api",framework==null?-1:framework.getApiVersion());
        b.putString("phase",runtimeFault.isEmpty()?installation.phase():"degraded");b.putString("runtime_fault",runtimeFault);b.putString("event",latestEvent);
        b.putString("adapter",features==null?"":DialerFeatures.ADAPTER);
        b.putBoolean("features_ready",features!=null);
        b.putStringArray("hooks",installation.completed());b.putBoolean("audio_ready",audioReady);b.putString("audio_reason",audioReason);
        b.putBoolean("endpoint_ready",endpointReady);b.putBoolean("legacy_audio_ready",legacyAudioReady);
        b.putString("diagnostics",diagnostics.describe(SystemClock.elapsedRealtime()));b.putBoolean("business_problem",diagnostics.hasCurrentFailure());
        b.putString("fallback_init",fallbackReason);b.putString("fallback_state",CarrierFallback.state());
        b.putBoolean("fallback_capable",CarrierFallback.capable());
        b.putLong("dialer_version",dialerVersion);
        b.putInt("ims",-1);b.putInt("pid",android.os.Process.myPid());return b;
    }
    private static void wakeAll(){List<Session> active=copy();if(active.size()==1)diagnostics.begin(active.get(0).diagnosticSession);for(Session s:active)s.queue();}
    private static void add(Call c,InCallService service) {
        if(!installation.ready()||!runtimeFault.isEmpty()||c==null||sessions.containsKey(c))return;
        Session s=new Session(c,service);sessions.put(c,s);
        try{c.registerCallback(s.callback,main);}catch(Throwable t){sessions.remove(c);report(s.diagnosticSession,DiagnosticState.Step.VIDEO_PROVIDER,DiagnosticState.State.FAILED,"call_callback_registration:"+errorType(t));diagnostics.end(s.diagnosticSession);status("callback_registration_failed");return;}
        status("call_observed voice_origin="+s.voiceOrigin);
        // Watchdog only while there are calls: stop a bound renderer when the switch is changed.
        if(watchdog==null){watchdog=new Runnable(){public void run(){
            if(sessions.isEmpty()){watchdog=null;return;}
            if(!enabled())for(Session active:copy()){active.cancelAudio();active.detach();}
            else wakeAll();
            main.postDelayed(this,1000);
        }};main.post(watchdog);}
        wakeAll();
    }
    private static void remove(Call c){Session s=sessions.remove(c);if(s!=null)s.close();wakeAll();if(sessions.isEmpty()){status("idle");CarrierFallback.schedule(app);}}
    private static boolean target(Call c) {
        try {
            Call.Details d=c.getDetails();
            if(d==null||d.getCallDirection()!=Call.Details.DIRECTION_OUTGOING||
                    d.hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL)||
                    d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)||
                    d.hasProperty(Call.Details.PROPERTY_IS_EXTERNAL_CALL)||c.getParent()!=null||!c.getChildren().isEmpty())return false;
            if(d.getHandle()==null||!"tel".equals(d.getHandle().getScheme()))return false;
            TelephonyManager tm=(TelephonyManager)app.getSystemService(Context.TELEPHONY_SERVICE);
            if(tm==null||tm.isEmergencyNumber(d.getHandle().getSchemeSpecificPart()))return false;
            PhoneAccountHandle a=d.getAccountHandle();
            if(a==null||!"com.android.phone".equals(a.getComponentName().getPackageName()))return false;
            SubscriptionManager sm=(SubscriptionManager)app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> list=sm.getActiveSubscriptionInfoList();
            if(list==null)return false;
            for(SubscriptionInfo i:list)if(i.getCarrierId()==2237&&"460".equals(i.getMccString())&&"11".equals(i.getMncString())&&
                    a.getId().equals(String.valueOf(i.getSubscriptionId())))return true;
        }catch(Throwable ignored){}
        return false;
    }
    private static void trace(InCallService.VideoCall vc) throws Exception {
        if(vc==null||traced.contains(vc.getClass()))return;
        VideoCallFeatures contract=VideoCallFeatures.probe(vc.getClass());
        List<HookRegistry<Method>.Lease> handles=new ArrayList<>();
        try{
        handles.add(intercept(contract.display,chain->{
            for(Session s:copy())if(s.provider==chain.getThisObject())s.ownsBinding=chain.getArg(0)!=null&&chain.getArg(0)==s.surface;
            return chain.proceed();
        }));
        handles.add(intercept(contract.modify,chain->{
            for(Session s:copy())if(s.provider==chain.getThisObject()){s.policy.userVideo();s.queue();status("user_video_request_native_only");}
            return chain.proceed();
        }));
        traced.add(vc.getClass());
        }catch(Throwable failure){for(HookRegistry<Method>.Lease handle:handles)try{handle.unhook();}catch(Throwable ignored){}throw new IllegalStateException("provider_contract_failed",failure);}
    }
    private static final class Session {
        final Call call;
        final boolean voiceOrigin;
        final SessionGate gate;
        final InCallService service;
        final EarlyMediaPolicy policy;
        final long diagnosticSession=++nextSession;
        final AudioRestoreAttempt audioAttempt=new AudioRestoreAttempt();
        long audioWaitSince=-1;
        boolean providerReady;
        String providerFailure="provider_not_initialized";
        boolean viewRejected;
        volatile InCallService.VideoCall provider;
        volatile Surface surface;
        TextureView texture;
        SurfaceView nativeSurfaceView;
        SurfaceTexture nativeTexture;
        TextureView.SurfaceTextureListener nativeListener;
        boolean nativeRenderer,ownsSurface;
        View placeholder;
        int placeholderVisibility;
        boolean placeholderOwned;
        TextView businessLabel;
        CharSequence nativeLabel;
        boolean fill=true;
        final Map<View,Drawable[]> changedBackgrounds=new IdentityHashMap<>();
        FrameLayout panel;
        Activity host;
        volatile boolean ownsBinding;
        boolean closed;
        boolean surfaceSubmitted,contentUpdated;
        int mediaWidth=540,mediaHeight=960;
        long submittedGeneration=-1;
        final Runnable pending=()->evaluate();
        final Call.Callback callback=new Call.Callback(){
            public void onStateChanged(Call c,int state){if(state==Call.STATE_ACTIVE||state==Call.STATE_DISCONNECTED){
                boolean bridged=surfaceSubmitted;gate.stop();cancelAudio();detach();unregisterVideo();
                report(diagnosticSession,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.DISABLED,"native_call_state");
                Call.Details d=c.getDetails();status((bridged?"native_handoff":"native_call_state")+" state="+state+" video="+(d==null?-1:d.getVideoState()));
            }else queue();}
            public void onDetailsChanged(Call c,Call.Details d){queue();}
            public void onVideoCallChanged(Call c,InCallService.VideoCall vc){queue();}
            public void onCallDestroyed(Call c){remove(c);}
        };
        final InCallService.VideoCall.Callback videoCallback=new InCallService.VideoCall.Callback(){
            public void onCallDataUsageChanged(long bytes){}
            public void onCameraCapabilitiesChanged(VideoProfile.CameraCapabilities capabilities){}
            public void onSessionModifyRequestReceived(VideoProfile profile){queue();}
            public void onSessionModifyResponseReceived(int result,VideoProfile requested,VideoProfile response){queue();}
            public void onVideoQualityChanged(int quality){status("video_quality code="+quality);}
            public void onCallSessionEvent(int event){status("provider_event code="+event);}
            public void onPeerDimensionsChanged(int w,int h){if(w>0&&h>0){mediaWidth=w;mediaHeight=h;fitTexture();}status("remote_dimensions w="+w+" h="+h);queue();}
        };
        Session(Call call,InCallService service){this.call=call;this.service=service;diagnostics.begin(diagnosticSession);Call.Details d=call.getDetails();
            int requested=d!=null&&d.getIntentExtras()!=null?d.getIntentExtras().getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,0):0;
            voiceOrigin=d!=null&&requested==0&&d.getVideoState()==VideoProfile.STATE_AUDIO_ONLY&&d.getCallDirection()==Call.Details.DIRECTION_OUTGOING;
            int initial=0;
            try{initial=audioRoute();}catch(Throwable t){report(diagnosticSession,DiagnosticState.Step.AUDIO_STATE,DiagnosticState.State.FAILED,"initial_audio_read:"+errorType(t));}
            policy=new EarlyMediaPolicy(voiceOrigin,requested,initial);gate=new SessionGate(voiceOrigin);
            status("call_business_origin requested_video="+requested+" initial_video="+(d==null?-1:d.getVideoState()));
        }
        void queue(){if(!closed){main.removeCallbacks(pending);main.post(pending);}}
        void evaluate(){
            if(closed)return;
            DiagnosticState.Step step=DiagnosticState.Step.VIDEO_PROVIDER;
            String operation="session_read";
            try {
                Call.Details details=call.getDetails();
                if(details!=null&&details.getIntentExtras()!=null&&details.getIntentExtras().getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,0)!=0)policy.userVideo();
                int route=0,supported=0;
                try{
                    route=audioRoute();supported=audioSupported();
                    report(diagnosticSession,DiagnosticState.Step.AUDIO_STATE,route==0?DiagnosticState.State.WAITING:DiagnosticState.State.READY,route==0?"audio_state_unavailable":"audio_route_observed");
                }catch(Throwable t){report(diagnosticSession,DiagnosticState.Step.AUDIO_STATE,DiagnosticState.State.FAILED,"audio_state_read:"+errorType(t));}
                if(details!=null)policy.observe(call.getState(),details.getVideoState(),route);
                boolean eligible=sessions.size()==1&&target(call);
                if(!enabled()||!eligible||details==null||!policy.isEarlyMedia(call.getState(),details.getVideoState())){cancelAudio();detach();return;}
                InCallService.VideoCall current=call.getVideoCall();
                if(current!=provider){detach();unregisterVideo();provider=current;
                    if(provider!=null){operation="provider_contract";trace(provider);operation="provider_callback";provider.registerCallback(videoCallback,main);providerReady=true;
                        report(diagnosticSession,step,DiagnosticState.State.READY,"provider_callbacks_ready");}gate.update(provider,null);}
                if(provider==null){report(diagnosticSession,step,DiagnosticState.State.WAITING,"provider_not_received");return;}
                if(!providerReady){report(diagnosticSession,step,DiagnosticState.State.FAILED,providerFailure);return;}
                report(diagnosticSession,step,DiagnosticState.State.READY,"provider_callbacks_ready");
                updateAudio(eligible,route,supported);
                step=DiagnosticState.Step.REMOTE_VIEW;operation="renderer_attach";
                if(foreground==null){report(diagnosticSession,step,DiagnosticState.State.WAITING,"call_ui_not_foreground");return;}
                View root=foreground.findViewById(android.R.id.content);
                float minimum=root==null?Float.MAX_VALUE:root.getWidth()*root.getHeight()*0.25f;
                View remote=root==null?null:findRemote(root,minimum);
                View boundView=nativeSurfaceView!=null?nativeSurfaceView:texture;
                if(nativeRenderer&&(!textureReady()||nativeSourceChanged()||remote!=boundView))detach();
                if(!nativeRenderer&&panel!=null){
                    if(remote!=null){
                        detach();status("renderer_promoted_to_native");
                    }else if(root!=null&&hasLargeSurface(root,minimum)){
                        detach();status("renderer_unavailable reason=unidentified_native_surface_appeared");
                    }
                }
                if(surface==null&&panel==null)attach(foreground);
                long token=gate.update(provider,surface);
                boolean visible=textureReady()&&surface!=null&&surface.isValid();
                if(gate.canBind(token,enabled(),eligible,call.getState(),call.getDetails().getVideoState(),visible)) {
                    operation="surface_submit";
                    provider.setDisplaySurface(surface);gate.submitted();submittedGeneration=token;
                    surfaceSubmitted=true;contentUpdated=false;
                    status("surface_submitted generation="+token+" evidence=binder_request");
                }
                if(surfaceSubmitted)presentEarlyMedia(foreground);
            }catch(Throwable t){
                String why=t instanceof VideoCallFeatures.Missing?((VideoCallFeatures.Missing)t).reason:operation+":"+errorType(t);
                if(step==DiagnosticState.Step.VIDEO_PROVIDER&&!providerReady)providerFailure=why;
                report(diagnosticSession,step,DiagnosticState.State.FAILED,why);status("bridge_failed step="+why);detach();
            }
        }
        int audioRoute(){
            if(endpointReady){int current=CallEndpointRouter.route(service);if(current!=0)return current;}
            if(legacyAudioReady){CallAudioState audio=service.getCallAudioState();return audio==null?0:audio.getRoute();}
            return 0;
        }
        int audioSupported(){
            if(endpointReady&&CallEndpointRouter.observed(service))return CallEndpointRouter.earpiece(service)==null?0:CallAudioState.ROUTE_EARPIECE;
            if(legacyAudioReady){CallAudioState audio=service.getCallAudioState();return audio==null?0:audio.getSupportedRouteMask();}
            return 0;
        }
        void cancelAudio(){
            if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::cancelAudio);return;}
            audioAttempt.cancel();
            if(audioAttempt.state()==AudioRestoreAttempt.State.CANCELLED)report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.CANCELLED,"session_or_user_changed");
        }
        void audioDiagnostic(){
            AudioRestoreAttempt.State state=audioAttempt.state();
            DiagnosticState.State display=state==AudioRestoreAttempt.State.TIMED_OUT||state==AudioRestoreAttempt.State.FAILED?DiagnosticState.State.FAILED:
                state==AudioRestoreAttempt.State.IDLE?DiagnosticState.State.NOT_CHECKED:DiagnosticState.State.valueOf(state.name());
            report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,display,audioAttempt.backend()+":"+audioAttempt.reason());
        }
        void updateAudio(boolean eligible,int route,int supported){
            if(!audioReady){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.DISABLED,"audio_hooks_unavailable");return;}
            boolean flag=audioPreserveEnabled();Call.Details details=call.getDetails();
            if(!flag||details==null||!policy.mayControlAudio(call.getState(),details.getVideoState())){cancelAudio();return;}
            long now=SystemClock.elapsedRealtime();
            if(policy.attempted()){
                audioAttempt.tick(now);
                if(audioAttempt.observe(audioAttempt.token(),route==CallAudioState.ROUTE_EARPIECE,now))status("early_media_audio_restore_confirmed backend="+audioAttempt.backend());
                audioDiagnostic();return;
            }
            if(route==0){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.WAITING,"audio_state_unavailable");return;}
            if(endpointReady&&route==CallAudioState.ROUTE_SPEAKER&&supported==0){
                if(CallEndpointRouter.observed(service))report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"earpiece_missing_or_ambiguous");
                else{
                    if(audioWaitSince<0)audioWaitSince=now;
                    report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,now-audioWaitSince<2000?DiagnosticState.State.WAITING:DiagnosticState.State.FAILED,
                        now-audioWaitSince<2000?"endpoint_list_pending":"endpoint_list_unavailable");
                }
                return;
            }
            if(!policy.shouldRestore(flag,eligible,call.getState(),details.getVideoState(),route,supported))return;
            try{
                CallEndpoint selected=null;
                if(endpointReady){
                    if(CallEndpointRouter.observed(service)){
                        selected=CallEndpointRouter.earpiece(service);
                        if(selected==null){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"earpiece_missing_or_ambiguous");return;}
                    }else{
                        if(audioWaitSince<0)audioWaitSince=now;
                        if(now-audioWaitSince<2000){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.WAITING,"endpoint_list_pending");return;}
                        if(!legacyAudioReady){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"endpoint_list_unavailable");return;}
                    }
                }
                if(selected==null&&!legacyAudioReady)return;
                if(!audioRelevant()||!enabled())return;
                String backend=selected==null?"legacy":"endpoint";
                final long token=audioAttempt.begin(backend,now);
                if(token<0)return;
                policy.requested();
                main.postDelayed(()->{if(!closed&&token==audioAttempt.token()){audioAttempt.tick(SystemClock.elapsedRealtime());audioDiagnostic();}},5000);
                changingAudio.set(true);
                try{
                    if(selected!=null)CallEndpointRouter.request(service,selected,new OutcomeReceiver<Void,CallEndpointException>(){
                        public void onResult(Void ignored){if(!audioRelevant()){cancelAudio();return;}if(audioAttempt.result(token,true,"",SystemClock.elapsedRealtime())){audioDiagnostic();queue();}}
                        public void onError(CallEndpointException failure){if(!audioRelevant()){cancelAudio();return;}if(audioAttempt.result(token,false,"endpoint_request_failed",SystemClock.elapsedRealtime()))audioDiagnostic();}
                    });
                    else service.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
                    status("early_media_audio_restore_requested backend="+backend);audioDiagnostic();
                }finally{changingAudio.remove();}
            }catch(Throwable failure){
                if(policy.attempted())audioAttempt.result(audioAttempt.token(),false,"request_failed:"+errorType(failure),SystemClock.elapsedRealtime());
                report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"request_failed:"+errorType(failure));
            }
        }
        boolean audioRelevant(){
            try{Call.Details details=call.getDetails();return !closed&&details!=null&&policy.mayControlAudio(call.getState(),details.getVideoState());}
            catch(Throwable failure){report(diagnosticSession,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"audio_context_read:"+errorType(failure));return false;}
        }
        void attach(Activity activity){
            View v=activity.findViewById(android.R.id.content);if(!(v instanceof FrameLayout)){report(diagnosticSession,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.FAILED,"content_root_unsupported");return;}
            View remote=findRemote(v,v.getWidth()*v.getHeight()*0.25f);
            if(remote!=null){
                viewRejected=false;
                report(diagnosticSession,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.READY,"native_remote_view");
                host=activity;nativeRenderer=true;
                int placeholderId=activity.getResources().getIdentifier("videocall_fullscreen_background","id",PKG);
                placeholder=placeholderId==0?null:activity.findViewById(placeholderId);
                if(placeholder!=null&&placeholder.getVisibility()!=View.GONE){
                    placeholderVisibility=placeholder.getVisibility();placeholderOwned=true;
                    changingView.set(true);try{placeholder.setVisibility(View.GONE);}finally{changingView.remove();}
                    status("native_placeholder_hidden");
                }
                if(remote instanceof TextureView){texture=(TextureView)remote;nativeTexture=texture.getSurfaceTexture();surface=new Surface(nativeTexture);ownsSurface=true;observeNativeFrames();}
                else {nativeSurfaceView=(SurfaceView)remote;surface=nativeSurfaceView.getHolder().getSurface();ownsSurface=false;}
                status("remote_view_attached renderer=native w="+remote.getWidth()+" h="+remote.getHeight());return;
            }
            // An unidentified or ambiguous native display must not be replaced by an owned fallback.
            if(hasLargeSurface(v,v.getWidth()*v.getHeight()*0.25f)){
                report(diagnosticSession,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.FAILED,"remote_view_unknown_or_ambiguous");
                if(!viewRejected){viewRejected=true;status("renderer_unavailable reason=remote_view_not_unique_or_identified");}return;
            }
            viewRejected=false;
            report(diagnosticSession,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.READY,"owned_fallback_view");
            host=activity;panel=new FrameLayout(activity);panel.setBackgroundColor(0xff111827);
            texture=new TextureView(activity);panel.addView(texture,new FrameLayout.LayoutParams(-1,-1));
            final TextureView owned=texture;
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);
            GestureDetector gestures=new GestureDetector(activity,new GestureDetector.SimpleOnGestureListener(){
                public boolean onDown(MotionEvent e){return true;}
                public boolean onDoubleTap(MotionEvent e){fill=!fill;fitTexture();status("video_scale mode="+(fill?"fill":"fit"));return true;}
            });
            texture.setOnTouchListener((view,event)->gestures.onTouchEvent(event));
            clearVideoBackdrops(v);
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){if(texture!=owned||closed)return;surface=new Surface(st);ownsSurface=true;gate.update(provider,surface);queue();}
                public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){if(texture==owned){fitTexture();queue();}}
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st){if(texture==owned)releaseSurface();return true;}
                public void onSurfaceTextureUpdated(SurfaceTexture st){
                    if(texture==owned&&surface!=null&&submittedGeneration==gate.generation()){
                        contentUpdated=true;status("surface_content_updated generation="+submittedGeneration+" evidence=texture_update_not_visual_acceptance");submittedGeneration=-1;
                    }
                }
            });
            texture.addOnLayoutChangeListener((a,b,c,d,e,f,g,h,i)->queue());
            View.OnLayoutChangeListener sizing=(a,b,c,d,e,f,g,h,i)->{if(panel!=null&&panel.getParent()==v)resizePanel(v);};
            v.addOnLayoutChangeListener(sizing);
            panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){v.removeOnLayoutChangeListener(sizing);}});
            ((FrameLayout)v).addView(panel,0,p);resizePanel(v);status("remote_view_attached renderer=background_fullscreen native_controls=foreground");
        }
        void resizePanel(View parent){
            if(panel==null||host==null||parent.getHeight()<=0)return;
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
            if(p.height!=-1||p.width!=-1){p.height=-1;p.width=-1;panel.setLayoutParams(p);}fitTexture();
        }
        void presentEarlyMedia(Activity activity){
            if(activity==null)return;
            int id=activity.getResources().getIdentifier("contactgrid_status_text","id",PKG);
            View label=id==0?null:activity.findViewById(id);
            if(label instanceof TextView){
                businessLabel=(TextView)label;
                if(!"正在语音呼叫 · 视频彩铃".contentEquals(businessLabel.getText())){
                    nativeLabel=businessLabel.getText();changingView.set(true);
                    try{businessLabel.setText("正在语音呼叫 · 视频彩铃");}finally{changingView.remove();}
                }
            }
        }
        void fitTexture(){
            if(nativeRenderer||texture==null||texture.getWidth()==0||texture.getHeight()==0)return;
            float viewAspect=(float)texture.getWidth()/texture.getHeight();float mediaAspect=(float)mediaWidth/mediaHeight;
            float sx=1,sy=1;
            if(fill){if(viewAspect>mediaAspect)sy=viewAspect/mediaAspect;else sx=mediaAspect/viewAspect;}
            else {if(viewAspect>mediaAspect)sx=mediaAspect/viewAspect;else sy=viewAspect/mediaAspect;}
            Matrix matrix=new Matrix();matrix.setScale(sx,sy,texture.getWidth()/2f,texture.getHeight()/2f);texture.setTransform(matrix);
        }
        void clearVideoBackdrops(View view){
            if(view==panel)return;
            if(view instanceof ViewGroup){
                Drawable old=view.getBackground();
                if(old instanceof ColorDrawable&&((ColorDrawable)old).getColor()==android.graphics.Color.BLACK){
                    Drawable transparent=new ColorDrawable(android.graphics.Color.TRANSPARENT);
                    changedBackgrounds.put(view,new Drawable[]{old,transparent});view.setBackground(transparent);
                }
                ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++)clearVideoBackdrops(g.getChildAt(n));
            }
        }
        View findRemote(View view,float minimum){
            int id=view.getResources().getIdentifier("videocall_video_remote","id",PKG);
            View anchor=id==0?null:view.findViewById(id);
            if(anchor==null)return null;
            List<View> candidates=new ArrayList<>();collectRemote(anchor,minimum,candidates);
            return candidates.size()==1?candidates.get(0):null;
        }
        void collectRemote(View view,float minimum,List<View> candidates){
            if(view==panel||!view.isShown()||!view.isAttachedToWindow())return;
            String name="";try{if(view.getId()!=View.NO_ID)name=view.getResources().getResourceEntryName(view.getId()).toLowerCase(java.util.Locale.ROOT);}catch(Throwable ignored){}
            if(name.contains("preview")||name.contains("camera")||name.contains("local"))return;
            if(view.getWidth()*view.getHeight()>=minimum){
                if(view instanceof TextureView&&((TextureView)view).isAvailable()&&((TextureView)view).getSurfaceTexture()!=null)candidates.add(view);
                if(view instanceof SurfaceView&&((SurfaceView)view).getHolder().getSurface().isValid())candidates.add(view);
            }
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)collectRemote(group.getChildAt(n),minimum,candidates);}
        }
        boolean hasLargeSurface(View view,float minimum){
            if(view==panel||!view.isShown()||!view.isAttachedToWindow())return false;
            if((view instanceof TextureView||view instanceof SurfaceView)&&view.getWidth()*view.getHeight()>=minimum)return true;
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)if(hasLargeSurface(group.getChildAt(n),minimum))return true;}
            return false;
        }
        void observeNativeFrames(){
            nativeListener=texture.getSurfaceTextureListener();
            if(nativeListener==null||textureListeners.contains(nativeListener.getClass()))return;
            try{
                Method method=declared(nativeListener.getClass(),"onSurfaceTextureUpdated",SurfaceTexture.class);
                intercept(method,chain->{
                    Object result=chain.proceed();
                    for(Session s:copy())if(s.nativeRenderer&&s.nativeListener==chain.getThisObject()&&s.nativeTexture==chain.getArg(0)&&s.surfaceSubmitted&&!s.contentUpdated){
                        s.contentUpdated=true;status("surface_content_updated renderer=native evidence=texture_update_not_visual_acceptance");
                    }
                    return result;
                });textureListeners.add(nativeListener.getClass());
            }catch(Throwable t){android.util.Log.w(TAG,"Native texture observation unavailable",t);}
        }
        int controlTop(View view,View root){
            if(view==panel||!view.isShown())return -1;
            int top=-1;
            if(view.isClickable()&&view.isEnabled()&&view.getHeight()>=dp(host,24)&&view.getHeight()<=dp(host,120)){
                Rect r=new Rect(),parent=new Rect();if(view.getGlobalVisibleRect(r)&&root.getGlobalVisibleRect(parent)&&r.top-parent.top>dp(host,160))top=r.top-parent.top;
            }
            if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){int child=controlTop(g.getChildAt(n),root);if(child>0&&(top<0||child<top))top=child;}}
            return top;
        }
        boolean textureReady(){View view=nativeSurfaceView!=null?nativeSurfaceView:texture;return view!=null&&view.isAttachedToWindow()&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0;}
        boolean nativeSourceChanged(){return texture!=null?texture.getSurfaceTexture()!=nativeTexture:nativeSurfaceView!=null&&nativeSurfaceView.getHolder().getSurface()!=surface;}
        void releaseSurface(){
            if(surface==null)return;
            // Concrete setter trace tells whether native UI has since taken ownership.
            if(ownsBinding&&provider!=null){try{provider.setDisplaySurface(null);}catch(Throwable ignored){}}
            ownsBinding=false;if(ownsSurface)surface.release();surface=null;ownsSurface=false;gate.update(provider,null);submittedGeneration=-1;surfaceSubmitted=false;contentUpdated=false;
        }
        void detach(){main.removeCallbacks(pending);releaseSurface();
            changingView.set(true);
            try{
                if(businessLabel!=null&&nativeLabel!=null&&"正在语音呼叫 · 视频彩铃".contentEquals(businessLabel.getText()))businessLabel.setText(nativeLabel);
            }finally{changingView.remove();}
            businessLabel=null;nativeLabel=null;
            if(placeholder!=null&&placeholderOwned&&placeholder.getVisibility()==View.GONE){
                changingView.set(true);try{placeholder.setVisibility(placeholderVisibility);}finally{changingView.remove();}
            }
            placeholder=null;placeholderOwned=false;
            FrameLayout old=panel;panel=null;texture=null;nativeSurfaceView=null;nativeTexture=null;nativeListener=null;nativeRenderer=false;host=null;
            if(old!=null){ViewParent parent=old.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(old);}
            for(Map.Entry<View,Drawable[]> e:changedBackgrounds.entrySet())if(e.getKey().getBackground()==e.getValue()[1])e.getKey().setBackground(e.getValue()[0]);changedBackgrounds.clear();
        }
        void unregisterVideo(){providerReady=false;if(provider!=null){try{provider.unregisterCallback(videoCallback);}catch(Throwable ignored){}provider=null;}}
        void close(){closed=true;gate.stop();cancelAudio();detach();try{call.unregisterCallback(callback);}catch(Throwable ignored){}unregisterVideo();diagnostics.end(diagnosticSession);}
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
