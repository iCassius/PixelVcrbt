package com.antigravity.pixelvcrbt;

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
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Dialer-only early media renderer and bounded prerequisite fallback. No KSU files or handshake. */
public final class PixelVcrbtBridge implements IXposedHookLoadPackage {
    private static final String PKG="com.google.android.dialer", TAG="PixelVcrbtBridge";
    private static final String HOST="com.android.dialer.incall.activity.ui.InCallActivity";
    private static final Map<Call,Session> sessions=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<Class<?>> traced=new HashSet<>();
    private static final Set<Class<?>> textureListeners=new HashSet<>();
    private static Handler main;
    private static Application app;
    private static Activity foreground;
    private static boolean installed;
    private static Runnable watchdog;
    private static String latestEvent="idle";
    private static String initReason="initializing";
    private static final ThreadLocal<Boolean> changingView=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> changingAudio=new ThreadLocal<>();

    private static void status(String state) {
        latestEvent=state;
        if(state.startsWith("version_mismatch")||state.startsWith("device_build_mismatch")||state.startsWith("init_failed"))initReason=state;
        android.util.Log.i(TAG,"VCRBT_STATUS at="+System.currentTimeMillis()+" pid="+android.os.Process.myPid()+" "+state);
    }
    private static boolean enabled() {
        try {
            Method get=Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class);
            String v=(String)get.invoke(null,"persist.sys.vcrbt.disabled","0");
            return "0".equals(v) && !new java.io.File(app.getFilesDir(),"vcrbt_disabled").exists();
        }catch(Throwable t){return false;}
    }
    private static boolean audioPreserveEnabled(){
        try{return "1".equals(Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"persist.sys.vcrbt.audio_preserve","1"));}
        catch(Throwable t){return false;}
    }
    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!PKG.equals(p.packageName)||!PKG.equals(p.processName))return;
        XposedHelpers.findAndHookMethod(Instrumentation.class,"callApplicationOnCreate",Application.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam h){initialize((Application)h.args[0],p.classLoader);}
        });
        try {
            Application current=(Application)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if(current!=null)initialize(current,p.classLoader);
        }catch(Throwable ignored){}
    }
    private static Method declared(Class<?> type,String name,Class<?>...parameters)throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try {return c.getDeclaredMethod(name,parameters);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    private static void hookNamed(Class<?> type,String name,XC_MethodHook hook)throws Exception{
        for(Class<?> c=type;c!=null;c=c.getSuperclass()){
            boolean found=false;for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)){XposedBridge.hookMethod(m,hook);found=true;}
            if(found)return;
        }
        throw new NoSuchMethodException(name);
    }
    private static void initialize(Application context,ClassLoader loader) {
        if(installed||context==null)return;
        try {
            if(app==null){app=context;main=new Handler(Looper.getMainLooper());RuntimeProbe.install(context,main,PixelVcrbtBridge::snapshot);}
            PackageInfo pi=context.getPackageManager().getPackageInfo(PKG,0);
            if(pi.getLongVersionCode()!=20148463L){status("version_mismatch");return;}
            if(!"mustang".equals(Build.DEVICE)||!"CP3A.260905.009".equals(Build.DISPLAY)){status("device_build_mismatch");return;}
            if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(()->initialize(context,loader));return;}
            Class<?> service=Class.forName("com.android.incallui.InCallServiceImpl",false,loader);
            if(!InCallService.class.isAssignableFrom(service))throw new IllegalStateException("InCallService signature");
            Class<?> host=Class.forName(HOST,false,loader);
            if(!Activity.class.isAssignableFrom(host))throw new IllegalStateException("Host signature");
            Method added=declared(service,"onCallAdded",Call.class),removed=declared(service,"onCallRemoved",Call.class);
            if(added.getReturnType()!=void.class||removed.getReturnType()!=void.class)throw new IllegalStateException("Callback signature");
            app=context;main=new Handler(Looper.getMainLooper());
            XposedHelpers.findAndHookMethod(View.class,"setVisibility",int.class,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam h){
                    if(!Boolean.TRUE.equals(changingView.get()))for(Session s:copy()){
                        if(h.thisObject==s.placeholder)s.placeholderOwned=false;
                    }
                }
            });
            XposedHelpers.findAndHookMethod(TextView.class,"setText",CharSequence.class,TextView.BufferType.class,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam h){if(!Boolean.TRUE.equals(changingView.get()))for(Session s:copy())
                    if(h.thisObject==s.businessLabel&&s.surfaceSubmitted){s.nativeLabel=(CharSequence)h.args[0];h.args[0]="正在语音呼叫 · 视频彩铃";}
                }
            });
            XposedBridge.hookMethod(added,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam h){
                    Call c=(Call)h.args[0];InCallService service=(InCallService)h.thisObject;main.post(()->add(c,service));
                }
            });
            XposedBridge.hookMethod(removed,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam h){
                    Call c=(Call)h.args[0];main.post(()->remove(c));
                }
            });
            context.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){if(HOST.equals(a.getClass().getName())){foreground=a;wakeAll();}}
                public void onActivityPaused(Activity a){if(foreground==a){foreground=null;for(Session s:copy())s.detach();}}
                public void onActivityDestroyed(Activity a){if(foreground==a)foreground=null;for(Session s:copy())if(s.host==a)s.detach();}
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
            });
            XC_MethodHook audioRequest=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam h){
                if(!Boolean.TRUE.equals(changingAudio.get()))for(Session s:copy())if(s.service==h.thisObject){s.policy.userAudio();status("user_audio_selection_preserved");}
            }};
            hookNamed(InCallService.class,"setAudioRoute",audioRequest);
            hookNamed(InCallService.class,"requestCallEndpointChange",audioRequest);
            hookNamed(InCallService.class,"requestBluetoothAudio",audioRequest);
            installed=true;status("hooks_ready version=20148463 renderer=native_preferred business_policy=early_media");
            initReason="ready";
            CarrierFallback.schedule(context);
            context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){CarrierFallback.schedule(app);}},
                new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED),null,main,Context.RECEIVER_EXPORTED);

        }catch(Throwable t){status("init_failed error="+t.getClass().getSimpleName());android.util.Log.e(TAG,"Initialization failure",t);}
    }
    private static List<Session> copy(){synchronized(sessions){return new ArrayList<>(sessions.values());}}
    private static Bundle snapshot(){
        Bundle b=new Bundle();b.putBoolean("hook",installed);b.putString("init",initReason);
        b.putInt("ims",-1);b.putInt("pid",android.os.Process.myPid());return b;
    }
    private static void wakeAll(){for(Session s:copy())s.queue();}
    private static void add(Call c,InCallService service) {
        if(c==null||sessions.containsKey(c))return;
        Session s=new Session(c,service);sessions.put(c,s);
        try{c.registerCallback(s.callback,main);}catch(Throwable t){sessions.remove(c);status("callback_registration_failed");return;}
        status("call_observed voice_origin="+s.voiceOrigin);
        // Watchdog only while there are calls: stop a bound renderer when the switch is changed.
        if(watchdog==null){watchdog=new Runnable(){public void run(){
            if(sessions.isEmpty()){watchdog=null;return;}
            if(!enabled())for(Session active:copy())active.detach();
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
        Method m=declared(vc.getClass(),"setDisplaySurface",Surface.class);
        XposedBridge.hookMethod(m,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam h){
                for(Session s:copy())if(s.provider==h.thisObject)s.ownsBinding=h.args[0]!=null&&h.args[0]==s.surface;
            }
        });
        traced.add(vc.getClass());
        hookNamed(vc.getClass(),"sendSessionModifyRequest",new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam h){
                for(Session s:copy())if(s.provider==h.thisObject){s.policy.userVideo();s.queue();status("user_video_request_native_only");}
            }
        });
    }
    private static final class Session {
        final Call call;
        final boolean voiceOrigin;
        final SessionGate gate;
        final InCallService service;
        final EarlyMediaPolicy policy;
        boolean audioRestoreConfirmed;
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
                boolean bridged=surfaceSubmitted;gate.stop();detach();unregisterVideo();
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
        Session(Call call,InCallService service){this.call=call;this.service=service;Call.Details d=call.getDetails();
            int requested=d!=null&&d.getIntentExtras()!=null?d.getIntentExtras().getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,0):0;
            voiceOrigin=d!=null&&requested==0&&d.getVideoState()==VideoProfile.STATE_AUDIO_ONLY&&d.getCallDirection()==Call.Details.DIRECTION_OUTGOING;
            CallAudioState audio=service.getCallAudioState();policy=new EarlyMediaPolicy(voiceOrigin,requested,audio==null?0:audio.getRoute());gate=new SessionGate(voiceOrigin);
            status("call_business_origin requested_video="+requested+" initial_video="+(d==null?-1:d.getVideoState()));
        }
        void queue(){if(!closed){main.removeCallbacks(pending);main.post(pending);}}
        void evaluate(){
            if(closed)return;
            try {
                Call.Details details=call.getDetails();
                if(details!=null&&details.getIntentExtras()!=null&&details.getIntentExtras().getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,0)!=0)policy.userVideo();
                CallAudioState audio=service.getCallAudioState();
                if(details!=null)policy.observe(call.getState(),details.getVideoState(),audio==null?0:audio.getRoute());
                boolean eligible=sessions.size()==1&&target(call);
                if(!enabled()||!eligible||details==null||!policy.isEarlyMedia(call.getState(),details.getVideoState())){detach();return;}
                if(audio!=null&&policy.shouldRestore(audioPreserveEnabled(),eligible,call.getState(),details.getVideoState(),audio.getRoute(),audio.getSupportedRouteMask())){
                    policy.requested();changingAudio.set(true);
                    try{service.setAudioRoute(CallAudioState.ROUTE_EARPIECE);status("early_media_audio_restore_requested");}finally{changingAudio.remove();}
                }
                if(policy.attempted()&&!audioRestoreConfirmed&&audio!=null&&audio.getRoute()==CallAudioState.ROUTE_EARPIECE){audioRestoreConfirmed=true;status("early_media_audio_restore_confirmed");}
                InCallService.VideoCall current=call.getVideoCall();
                if(current!=provider){detach();unregisterVideo();provider=current;
                    if(provider!=null){trace(provider);provider.registerCallback(videoCallback,main);}gate.update(provider,null);}
                if(provider==null||foreground==null)return;
                if(nativeRenderer&&(!textureReady()||nativeSourceChanged()))detach();
                if(!nativeRenderer&&panel!=null){
                    View root=foreground.findViewById(android.R.id.content);
                    if(root!=null&&findRemote(root,root.getWidth()*root.getHeight()*0.25f)!=null){
                        detach();status("renderer_promoted_to_native");
                    }
                }
                if(surface==null&&panel==null)attach(foreground);
                long token=gate.update(provider,surface);
                boolean visible=textureReady()&&surface!=null&&surface.isValid();
                if(gate.canBind(token,enabled(),eligible,call.getState(),call.getDetails().getVideoState(),visible)) {
                    provider.setDisplaySurface(surface);gate.submitted();submittedGeneration=token;
                    surfaceSubmitted=true;contentUpdated=false;
                    status("surface_submitted generation="+token+" evidence=binder_request");
                }
                if(surfaceSubmitted)presentEarlyMedia(foreground);
            }catch(Throwable t){status("bridge_failed error="+t.getClass().getSimpleName());detach();}
        }
        void attach(Activity activity){
            View v=activity.findViewById(android.R.id.content);if(!(v instanceof FrameLayout))return;
            View remote=findRemote(v,v.getWidth()*v.getHeight()*0.25f);
            if(remote!=null){
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
            if(view==panel||!view.isShown()||!view.isAttachedToWindow())return null;
            String name="";try{if(view.getId()!=View.NO_ID)name=view.getResources().getResourceEntryName(view.getId()).toLowerCase(java.util.Locale.ROOT);}catch(Throwable ignored){}
            if(name.contains("preview")||name.contains("camera")||name.contains("local"))return null;
            if(view.getWidth()*view.getHeight()>=minimum){
                if(view instanceof TextureView&&((TextureView)view).isAvailable()&&((TextureView)view).getSurfaceTexture()!=null)return view;
                if(view instanceof SurfaceView&&((SurfaceView)view).getHolder().getSurface().isValid())return view;
            }
            View best=null;
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View found=findRemote(group.getChildAt(n),minimum);if(found!=null&&(best==null||found.getWidth()*found.getHeight()>best.getWidth()*best.getHeight()))best=found;}}
            return best;
        }
        void observeNativeFrames(){
            nativeListener=texture.getSurfaceTextureListener();
            if(nativeListener==null||textureListeners.contains(nativeListener.getClass()))return;
            try{
                Method method=declared(nativeListener.getClass(),"onSurfaceTextureUpdated",SurfaceTexture.class);
                XposedBridge.hookMethod(method,new XC_MethodHook(){
                    protected void afterHookedMethod(MethodHookParam p){
                        for(Session s:copy())if(s.nativeRenderer&&s.nativeListener==p.thisObject&&s.nativeTexture==p.args[0]&&s.surfaceSubmitted&&!s.contentUpdated){
                            s.contentUpdated=true;status("surface_content_updated renderer=native evidence=texture_update_not_visual_acceptance");
                        }
                    }
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
        void unregisterVideo(){if(provider!=null){try{provider.unregisterCallback(videoCallback);}catch(Throwable ignored){}provider=null;}}
        void close(){closed=true;gate.stop();detach();try{call.unregisterCallback(callback);}catch(Throwable ignored){}unregisterVideo();}
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
