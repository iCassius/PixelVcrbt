package com.antigravity.pixelvcrbt;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Direct public-API readback; no Dialer launch, cached broadcast, KSU file or su. */
public final class DebugActivity extends Activity {
    private LinearLayout rows;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean pending;
    private boolean readAgain;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ParameterReader.Result last=new ParameterReader.Result();
    private Bundle runtime;
    private String nonce;
    private boolean listening;
    private String answered;
    private final BroadcastReceiver reply=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(nonce!=null&&nonce.equals(i.getStringExtra("nonce"))){answered=nonce;runtime=i.getBundleExtra("result");show(last);}
    }};
    private final Runnable poll=new Runnable(){public void run(){
        nonce=java.util.UUID.randomUUID().toString();
        final String requested=nonce;
        sendBroadcast(new Intent(RuntimeProbe.QUERY).setPackage(RuntimeProbe.DIALER).putExtra("nonce",nonce));
        main.postDelayed(()->{if(requested.equals(nonce)&&!requested.equals(answered)){runtime=null;show(last);}},1500);
        main.postDelayed(this,3000);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff101827);
        root.setPadding(dp(16),dp(32),dp(16),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(16),bars.top+dp(14),dp(16),bars.bottom+dp(12));return insets;
        });
        TextView title=new TextView(this);title.setText("Pixel彩铃激活模块");title.setTextSize(24);title.setTextColor(Color.WHITE);root.addView(title);
        ScrollView scroll=new ScrollView(this);rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=dp(12);root.addView(scroll,p);setContentView(root);
        show(new ParameterReader.Result());
    }
    protected void onResume(){
        super.onResume();
        runtime=null;
        registerReceiver(reply,new IntentFilter(RuntimeProbe.REPLY),"android.permission.READ_PRIVILEGED_PHONE_STATE",main,Context.RECEIVER_EXPORTED);
        listening=true;main.post(poll);
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED&&
                !getPreferences(0).getBoolean("phone_permission_requested",false)){
            getPreferences(0).edit().putBoolean("phone_permission_requested",true).apply();
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},1);
        }
        read();
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);read();}
    private void read(){
        if(pending){readAgain=true;return;}pending=true;
        worker.execute(()->{
            ParameterReader.Result result=ParameterReader.read(this);
            runOnUiThread(()->{pending=false;if(!isFinishing()&&!isDestroyed()){
                last=result;show(result);if(readAgain){readAgain=false;read();}
            }});
        });
    }
    private void show(ParameterReader.Result result){
        rows.removeAllViews();
        section("本模块提供能力");fields(ParameterReader.MODULE_FIELDS,result,false);
        section("其他模块提供能力");fields(ParameterReader.PROVIDER_FIELDS,result,true);
        section("实际运行检测");
        boolean hook=runtime!=null&&runtime.getBoolean("hook");
        int ims=runtime==null?-1:runtime.getInt("ims",-1),calls=runtime==null?0:runtime.getInt("calls");
        String message=RuntimeReason.reason(hook,ims,calls,runtime!=null&&runtime.getBoolean("eligible"),
            runtime!=null&&runtime.getBoolean("early"),runtime!=null&&runtime.getBoolean("surface"),runtime!=null&&runtime.getBoolean("frames"));
        runtimeRow("Dialer 模块加载",hook?"已生效":"未检测到");
        runtimeRow("IMS 注册",ims==2?"已注册":ims==0?"未注册":ims==1?"注册中":"未检测到");
        runtimeRow("彩铃接收视频会话",runtime!=null&&runtime.getBoolean("early")?"已建立":"未检测到");
        runtimeRow("本次彩铃画面更新",runtime!=null&&runtime.getBoolean("frames")?"已检测到":"未检测到");
        if(runtime!=null&&!hook){String why=runtime.getString("init", "unknown");
            message="模块适配失败："+(why.equals("version_mismatch")?"Google 电话版本不兼容":why.equals("device_build_mismatch")?"设备或系统版本不兼容":"Hook 初始化失败，请检查 ADB 日志")+"；需兼容版本，扩大作用域不能解决";}
        if(hook&&runtime.getInt("call_state",-1)==android.telecom.Call.STATE_ACTIVE)message="通话已接通，彩铃阶段结束；视频会话与画面检测已清除";
        if(hook&&ims<0)message+="；IMS 注册信息不可读取，需通过 ADB 日志核验，不能判为未注册";
        boolean phonePermission=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
        if(!phonePermission)message="未授予电话状态权限：请在系统设置中允许该权限，才能读取前置配置和查询模块响应";
        TextView note=new TextView(this);note.setText(message);note.setTextSize(13);note.setTextColor(0xffb9c6d8);rows.addView(note);
        boolean missing=false;for(String[] f:ParameterReader.PROVIDER_FIELDS)missing|=!Boolean.TRUE.equals(result.values.get(f[0]));
        if(missing&&phonePermission){TextView hint=new TextView(this);hint.setText(runtime!=null&&runtime.getBoolean("fallback_attempted")?"前置配置仍未齐：本次开机已尝试兜底，不反复覆盖；需其他模块提供。":result.fallbackAvailable?"前置配置未齐：空闲时启动电话可触发一次兜底；仍未齐需其他模块提供。":"前置配置未齐：需要系统或其他模块提供。" );hint.setTextColor(0xfffacc15);rows.addView(hint);}
    }
    private void runtimeRow(String label,String value){TextView t=new TextView(this);t.setText(label+"  "+value);t.setTextSize(14);t.setTextColor(Color.WHITE);t.setPadding(0,dp(5),0,dp(5));rows.addView(t);}
    private void section(String text){TextView title=new TextView(this);title.setText(text);title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(0,dp(10),0,dp(8));rows.addView(title);}
    private void fields(String[][] fields,ParameterReader.Result result,boolean provider){
        for(String[] field:fields){
            Object value=result.values.get(field[0]);String state=ParameterReader.state(value);
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(0xff1e293b);
            LinearLayout header=new LinearLayout(this);header.setOrientation(0);
            TextView name=new TextView(this);name.setText(field[0]);name.setTypeface(Typeface.MONOSPACE);name.setTextSize(13);name.setTextColor(Color.WHITE);
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView status=new TextView(this);status.setText(state);status.setTextSize(13);status.setSingleLine(true);status.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-2,-2);statusParams.leftMargin=dp(8);header.addView(status,statusParams);card.addView(header);
            String purpose=field[1];
            if(provider&&!Boolean.TRUE.equals(value))purpose=(result.fallbackAvailable?"缺失时由 Dialer 兜底：":"需其他模块开启：")+purpose;
            if(!provider&&field[0].equals("vcrbt_carrier_fallback_enabled_bool")&&!result.fallbackAvailable)purpose="当前无法兜底，需其他模块开启前置配置";
            TextView function=new TextView(this);function.setText("功能："+purpose);function.setTextSize(13);function.setTextColor(0xffb9c6d8);
            function.setSingleLine(true);function.setEllipsize(TextUtils.TruncateAt.END);card.addView(function);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);rows.addView(card,p);
        }
    }
    protected void onPause(){main.removeCallbacksAndMessages(null);nonce=null;runtime=null;if(listening){unregisterReceiver(reply);listening=false;}super.onPause();}
    protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
