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
    private boolean checking=true,reading=true;
    private String rendered;
    private final BroadcastReceiver reply=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(nonce!=null&&nonce.equals(i.getStringExtra("nonce"))){
            Bundle incoming=i.getBundleExtra("result");if(incoming==null)return;
            if(incoming.getBoolean("ims_pending")&&runtime!=null&&incoming.getInt("pid",-1)==runtime.getInt("pid",-2)&&runtime.getInt("ims",-1)>=0)
                incoming.putInt("ims",runtime.getInt("ims"));
            answered=nonce;runtime=incoming;checking=false;show(last);
        }
    }};
    private final Runnable poll=new Runnable(){public void run(){
        nonce=java.util.UUID.randomUUID().toString();
        final String requested=nonce;
        sendBroadcast(new Intent(RuntimeProbe.QUERY).setPackage(RuntimeProbe.DIALER).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("nonce",nonce));
        main.postDelayed(()->{if(requested.equals(nonce)&&!requested.equals(answered)){runtime=null;checking=false;show(last);}},2000);
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
        checking=true;
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
        if(pending){readAgain=true;return;}pending=true;reading=true;show(last);
        worker.execute(()->{
            ParameterReader.Result result=ParameterReader.read(this);
            runOnUiThread(()->{pending=false;if(!isFinishing()&&!isDestroyed()){
                last=result;reading=false;show(result);if(readAgain){readAgain=false;read();}
            }});
        });
    }
    private void show(ParameterReader.Result result){
        boolean granted=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
        boolean loaded=runtime!=null&&runtime.getBoolean("hook");int registration=runtime==null?-1:runtime.getInt("ims",-1);
        String key=reading+"|"+result.values+"|"+result.compatible+"|"+result.configReady+"|"+result.targetFound+"|"+result.fallbackAvailable+"|"+
            DetectionStatus.connection(checking,granted,runtime!=null,loaded)+"|"+
            DetectionStatus.ims(checking,granted,runtime!=null,loaded,registration,runtime!=null&&runtime.getBoolean("ims_pending"))+"|"+
            (runtime==null?"":runtime.getString("init","")+runtime.getBoolean("fallback_attempted"));
        if(key.equals(rendered))return;rendered=key;
        rows.removeAllViews();
        section("本模块功能开关");fields(ParameterReader.MODULE_FIELDS,result,false);
        section("依赖项检测");fields(ParameterReader.PROVIDER_FIELDS,result,true);
        section("能力检测");
        boolean hook=runtime!=null&&runtime.getBoolean("hook");
        int ims=runtime==null?-1:runtime.getInt("ims",-1);
        boolean phonePermission=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
        runtimeRow("版本适配",reading?"检测中":result.compatible?"支持":"不支持");
        runtimeRow("模块连接",DetectionStatus.connection(checking,phonePermission,runtime!=null,hook));
        runtimeRow("IMS 注册",DetectionStatus.ims(checking,phonePermission,runtime!=null,hook,ims,runtime!=null&&runtime.getBoolean("ims_pending")));
        String message="";
        if(!reading&&!result.compatible)message="当前设备、系统或电话版本不支持。";
        else if(runtime==null&&!checking)message="电话进程未响应，不代表功能开关关闭；可打开电话后再次查看。";
        else if(hook&&ims==0)message="IMS 未注册，请检查运营商及 VoLTE／VoWiFi 配置。";
        else if(hook&&ims<0&&!runtime.getBoolean("ims_pending"))message="IMS 注册信息不可读取，不能判为未注册。";
        if(runtime!=null&&!hook){String why=runtime.getString("init", "unknown");
            message="模块适配失败："+(why.equals("version_mismatch")?"Google 电话版本不兼容":why.equals("device_build_mismatch")?"设备或系统版本不兼容":"Hook 初始化失败，请检查 ADB 日志")+"；需兼容版本，扩大作用域不能解决";}
        if(!phonePermission)message="未授予电话状态权限：请在系统设置中允许该权限，才能读取前置配置和查询模块响应";
        if(!message.isEmpty()){TextView note=new TextView(this);note.setText(message);note.setTextSize(13);note.setTextColor(0xffb9c6d8);rows.addView(note);}
        boolean missing=false;for(String[] f:ParameterReader.PROVIDER_FIELDS)missing|=!Boolean.TRUE.equals(result.values.get(f[0]));
        if(!reading&&missing&&phonePermission){TextView hint=new TextView(this);hint.setText(!result.targetFound?"未找到适配的电信卡。":!result.configReady?"运营商配置暂不可读取。":runtime!=null&&runtime.getBoolean("fallback_attempted")?"前置配置仍未齐：本次开机已尝试兜底，不反复覆盖；需其他模块提供。":result.fallbackAvailable?"前置配置未齐：空闲时启动电话可触发一次兜底；仍未齐需其他模块提供。":"前置配置未齐：需要系统或其他模块提供。" );hint.setTextColor(0xfffacc15);rows.addView(hint);}
    }
    private void runtimeRow(String label,String value){TextView t=new TextView(this);t.setText(label+"  "+value);t.setTextSize(14);t.setTextColor(Color.WHITE);t.setPadding(0,dp(5),0,dp(5));rows.addView(t);}
    private void section(String text){TextView title=new TextView(this);title.setText(text);title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(0,dp(10),0,dp(8));rows.addView(title);}
    private void fields(String[][] fields,ParameterReader.Result result,boolean provider){
        for(String[] field:fields){
            Object value=result.values.get(field[0]);String state=reading?"检测中":provider?ParameterReader.state(value):DetectionStatus.setting(value);
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(0xff1e293b);
            LinearLayout header=new LinearLayout(this);header.setOrientation(0);
            TextView name=new TextView(this);name.setText(field[0]);name.setTypeface(Typeface.MONOSPACE);name.setTextSize(13);name.setTextColor(Color.WHITE);
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView status=new TextView(this);status.setText(state);status.setTextSize(13);status.setSingleLine(true);status.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-2,-2);statusParams.leftMargin=dp(8);header.addView(status,statusParams);card.addView(header);
            String purpose=field[1];
            if(provider&&result.configReady&&!Boolean.TRUE.equals(value))purpose=(result.fallbackAvailable?"缺失时由 Dialer 兜底：":"需其他模块开启：")+purpose;
            TextView function=new TextView(this);function.setText("功能："+purpose);function.setTextSize(13);function.setTextColor(0xffb9c6d8);
            function.setSingleLine(true);function.setEllipsize(TextUtils.TruncateAt.END);card.addView(function);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);rows.addView(card,p);
        }
    }
    protected void onPause(){main.removeCallbacksAndMessages(null);nonce=null;runtime=null;if(listening){unregisterReceiver(reply);listening=false;}super.onPause();}
    protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
