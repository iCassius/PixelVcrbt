package io.github.icassius.pixelvcrbt;
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
    private final ExecutorService updates=Executors.newSingleThreadExecutor();
    private TextView updateStatus;
    private String currentVersion="unknown";
    private long currentCode=-1;
    private UpdateChecker.Result updateResult;
    private boolean updatePending;
    private long lastUpdateCheck;
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
    private final RuntimeReplyGate replyGate=new RuntimeReplyGate();
    private String runtimeIssue="";
    private Bundle history=new Bundle();
    private String environment="";
    private int boot=-1;
    private final BroadcastReceiver reply=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(RuntimeProbe.REPLY.equals(i.getAction())&&nonce!=null&&nonce.equals(i.getStringExtra("nonce"))){
            Bundle incoming=i.getBundleExtra("result");if(incoming==null)return;
            if(incoming.getInt("schema",-1)!=RuntimeReplyGate.SCHEMA){
                if(!replyGate.current(nonce,SystemClock.elapsedRealtime())||nonce.equals(answered))return;
                answered=nonce;runtime=null;checking=false;runtimeIssue="电话进程中的模块协议较旧或不兼容，需在空闲时重新加载电话进程。";show(last);return;
            }
            if(!replyGate.accept(nonce,incoming.getInt("schema"),incoming.getString("instance"),incoming.getLong("sequence",-1),SystemClock.elapsedRealtime()))return;
            if(incoming.getBoolean("ims_pending")&&runtime!=null&&incoming.getString("instance","").equals(runtime.getString("instance"))&&runtime.getInt("ims",-1)>=0)
                incoming.putInt("ims",runtime.getInt("ims"));
            runtimeIssue="";answered=nonce;runtime=incoming;checking=false;show(last);
        }
    }};
    private final Runnable poll=new Runnable(){public void run(){
        nonce=java.util.UUID.randomUUID().toString();
        replyGate.begin(nonce,SystemClock.elapsedRealtime(),2500);
        final String requested=nonce;
        sendBroadcast(new Intent(RuntimeProbe.QUERY).setPackage(RuntimeProbe.DIALER).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("nonce",nonce));
        main.postDelayed(()->{if(requested.equals(nonce)&&!requested.equals(answered)){runtime=null;runtimeIssue="";checking=false;show(last);}},2500);
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
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);currentVersion=info.versionName;currentCode=info.getLongVersionCode();}catch(Exception ignored){}
        LinearLayout versionRow=new LinearLayout(this);versionRow.setPadding(0,dp(8),0,0);
        TextView version=new TextView(this);version.setText("版本 "+currentVersion);version.setTextSize(12);version.setTextColor(0xffb9c6d8);versionRow.addView(version,new LinearLayout.LayoutParams(0,-2,1));
        updateStatus=new TextView(this);updateStatus.setText("检查更新");updateStatus.setTextSize(12);updateStatus.setTextColor(0xff93c5fd);updateStatus.setPadding(dp(8),0,0,0);
        updateStatus.setOnClickListener(v->{if(updatePending)return;if(updateResult!=null&&updateResult.url!=null){
            try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(updateResult.url)));}catch(ActivityNotFoundException ignored){updateStatus.setText("未找到浏览器");}
        }else checkUpdate();});versionRow.addView(updateStatus);root.addView(versionRow);
        ScrollView scroll=new ScrollView(this);rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=dp(12);root.addView(scroll,p);setContentView(root);
        show(new ParameterReader.Result());
        checkUpdate();
    }
    private void checkUpdate(){
        if(updatePending)return;updatePending=true;lastUpdateCheck=SystemClock.elapsedRealtime();updateStatus.setText("检查中…");
        updates.execute(()->{UpdateChecker.Result result=UpdateChecker.check(currentVersion,currentCode,getPackageName());runOnUiThread(()->{
            updatePending=false;if(!isFinishing()&&!isDestroyed()){updateResult=result;updateStatus.setText(result.text);}
        });});
    }
    protected void onResume(){
        super.onResume();
        if(SystemClock.elapsedRealtime()-lastUpdateCheck>300000)checkUpdate();
        runtime=null;
        runtimeIssue="";replyGate.clear();
        worker.execute(()->{
            String expected=RuntimeHistory.environment(this);int currentBoot=RuntimeHistory.boot(this);Bundle saved=RuntimeHistory.read(this);
            main.post(()->{if(!isFinishing()&&!isDestroyed()){environment=expected;boot=currentBoot;history=saved;show(last);}});
        });
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
        String key=reading+"|"+result.values+"|"+result.configReady+"|"+result.targetFound+"|"+fallbackAvailable(result)+"|"+
            connection(granted,loaded)+"|"+runtimeIssue+"|"+historyText()+"|"+(runtime!=null&&runtime.getBoolean("business_problem"))+"|"+
            DetectionStatus.ims(checking,granted,runtime!=null,loaded,registration,runtime!=null&&runtime.getBoolean("ims_pending"))+"|"+
            (runtime==null?"":runtime.getString("instance","")+runtime.getString("module_build","")+runtime.getString("phase","")+runtime.getString("runtime_fault","")+
                runtime.getString("init","")+runtime.getBoolean("fallback_attempted")+runtime.getBoolean("audio_ready")+runtime.getString("fallback_state","")+runtime.getBoolean("features_ready"));
        if(key.equals(rendered))return;rendered=key;
        rows.removeAllViews();
        section("本模块功能开关");fields(ParameterReader.MODULE_FIELDS,result,false);
        section("依赖项检测");fields(ParameterReader.PROVIDER_FIELDS,result,true);
        section("能力检测");
        boolean hook=runtime!=null&&runtime.getBoolean("hook");
        int ims=runtime==null?-1:runtime.getInt("ims",-1);
        boolean phonePermission=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
        runtimeRow("电话入口特征",runtime==null?"尚未确认":!currentRuntime()?"需重新加载":runtime.getBoolean("features_ready")?"必要项齐备":"failed".equals(runtime.getString("phase"))?"缺少必要项":"检测中");
        runtimeRow("模块连接",runtimeIssue.isEmpty()?connection(phonePermission,hook):"需重新加载");
        if(runtime==null&&!historyText().isEmpty())runtimeRow("上次确认",historyText());
        runtimeRow("IMS 注册",DetectionStatus.ims(checking,phonePermission,runtime!=null,hook,ims,runtime!=null&&runtime.getBoolean("ims_pending")));
        String message="";
        if(!runtimeIssue.isEmpty())message=runtimeIssue;
        else if(runtime!=null&&!currentRuntime())message="电话进程仍运行另一构建的模块，需在空闲时重新加载电话进程。";
        else if(runtime==null&&!checking)message="电话进程未响应，不代表功能开关关闭；可打开电话后再次查看。";
        else if(hook&&ims==0)message="IMS 未注册，请检查运营商及 VoLTE／VoWiFi 配置。";
        else if(hook&&ims<0&&!runtime.getBoolean("ims_pending"))message="IMS 注册信息不可读取，不能判为未注册。";
        if(runtime!=null&&currentRuntime()&&!hook&&"failed".equals(runtime.getString("phase")))message="模块适配失败：必要特征缺失或 Hook 安装失败；可查看诊断详情。";
        if(runtime!=null&&currentRuntime()&&"degraded".equals(runtime.getString("phase")))message="业务回调出现异常，本模块已停止接管；可查看诊断详情。";
        if(hook&&currentRuntime()&&!runtime.getBoolean("audio_ready"))message="必要 Hook 已加载；音频选择保护不可用，已停用自动音频恢复。";
        if(hook&&currentRuntime()&&runtime.getBoolean("business_problem"))message+=(message.isEmpty()?"":"\n")+"业务检查发现问题，请查看诊断详情；不代表功能开关被关闭。";
        if(!phonePermission)message="未授予电话状态权限：请在系统设置中允许该权限，才能读取前置配置和查询模块响应";
        if(!message.isEmpty()){TextView note=new TextView(this);note.setText(message);note.setTextSize(13);note.setTextColor(0xffb9c6d8);rows.addView(note);}
        if(runtime!=null){
            TextView detail=new TextView(this);detail.setText("诊断详情");detail.setTextColor(0xff93c5fd);detail.setPadding(0,dp(8),0,dp(8));
            detail.setOnClickListener(v->{if(runtime!=null)showDiagnostics(new Bundle(runtime));});rows.addView(detail);
        }
        boolean missing=false;for(String[] f:ParameterReader.PROVIDER_FIELDS)missing|=!Boolean.TRUE.equals(result.values.get(f[0]));
        if(!reading&&missing&&phonePermission){TextView hint=new TextView(this);hint.setText(!result.targetFound?"未找到适配的电信卡。":!result.configReady?"运营商配置暂不可读取。":runtime!=null&&runtime.getBoolean("fallback_attempted")?"前置配置仍未齐：兜底已提交或等待恢复检查，不持续覆盖；可查看诊断详情。":fallbackAvailable(result)?"前置配置未齐：已确认电话进程具备兜底方法，空闲时可有限尝试。":"前置配置未齐：尚未确认电话兜底能力，需系统或其他模块提供。" );hint.setTextColor(0xfffacc15);rows.addView(hint);}
    }
    private String historyText(){
        String status=HistoryPolicy.status(history.getString("environment",""),environment,history.getString("module_build",""),BuildIdentity.SOURCE,history.getInt("boot",-1),boot);
        if("none".equals(status))return "";
        String when=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(history.getLong("observed_at",0)));
        String result=history.getBoolean("hook")?"曾加载成功":"曾报告异常";
        return when+" "+result+"；"+("environment_changed".equals(status)?"环境变化，等待重新确认":"boot_changed".equals(status)?"重启后等待确认":"仅为历史记录");
    }
    private boolean currentRuntime(){return runtime!=null&&BuildIdentity.CODE==runtime.getLong("module_code",-1)&&BuildIdentity.SOURCE.equals(runtime.getString("module_build"))&&!environment.isEmpty()&&environment.equals(runtime.getString("environment"));}
    private boolean fallbackAvailable(ParameterReader.Result result){return result.fallbackAvailable&&currentRuntime()&&runtime.getBoolean("hook")&&runtime.getBoolean("fallback_capable");}
    private String connection(boolean permission,boolean hook){return DetectionStatus.connection(checking,permission,runtime!=null,hook,runtime==null?"":runtime.getString("phase",""),currentRuntime());}
    private void showDiagnostics(Bundle state){
        new android.app.AlertDialog.Builder(this).setTitle("模块运行诊断").setMessage(
            "电话版本："+state.getLong("dialer_version",-1)+"\n运行模块："+state.getString("module_name","")+" / "+state.getLong("module_code",-1)+
            "\n构建："+state.getString("module_build","")+"\n适配器："+state.getString("adapter","")+"\n安装阶段："+state.getString("phase","")+
            "\n结果："+state.getString("init","")+"\n运行异常："+state.getString("runtime_fault","")+"\n安装步骤："+java.util.Arrays.toString(state.getStringArray("hooks"))+
            "\n音频保护："+state.getString("audio_reason","")+"\n配置兜底："+state.getString("fallback_state","")+
            "\n音频路径：系统端点="+state.getBoolean("endpoint_ready")+"，旧方法="+state.getBoolean("legacy_audio_ready")+
            "\n\n"+state.getString("diagnostics","未提供业务诊断")+
            "\n最近业务事件："+state.getString("event","")+
            "\n已加载只表示必要 Hook 安装完成；实际画面与接通后的通话仍需业务验证。")
            .setPositiveButton("关闭",null).show();
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
            if(provider&&result.configReady&&!Boolean.TRUE.equals(value))purpose=(fallbackAvailable(result)?"缺失时由 Dialer 兜底：":"需其他模块开启：")+purpose;
            TextView function=new TextView(this);function.setText("功能："+purpose);function.setTextSize(13);function.setTextColor(0xffb9c6d8);
            function.setSingleLine(true);function.setEllipsize(TextUtils.TruncateAt.END);card.addView(function);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);rows.addView(card,p);
        }
    }
    protected void onPause(){main.removeCallbacksAndMessages(null);replyGate.clear();nonce=null;runtime=null;if(listening){unregisterReceiver(reply);listening=false;}super.onPause();}
    protected void onDestroy(){worker.shutdown();updates.shutdownNow();super.onDestroy();}
}
