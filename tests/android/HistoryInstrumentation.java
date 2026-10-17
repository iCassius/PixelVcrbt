package io.github.icassius.pixelvcrbt;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;

/** Runs against the installed signed APK. Restores the pre-test history file. */
public final class HistoryInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    public void onStart(){
        Bundle result=new Bundle();Context context=getTargetContext();AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"runtime-history.json"));
        byte[] original=null;boolean existed=false;
        try{
            try{original=file.readFully();existed=true;}catch(FileNotFoundException absent){}
            Class<?> history=context.getClassLoader().loadClass("io.github.icassius.pixelvcrbt.RuntimeHistory");
            Method save=history.getDeclaredMethod("save",Context.class,Bundle.class),read=history.getDeclaredMethod("read",Context.class);
            save.setAccessible(true);read.setAccessible(true);
            file.delete();
            check(((Bundle)read.invoke(null,context)).isEmpty(),"missing file remains unknown");
            Bundle receipt=new Bundle();receipt.putString("environment","fixture_environment");receipt.putString("module_build","fixture_module");
            receipt.putString("instance","fixture_process");receipt.putString("phase","ready");receipt.putBoolean("hook",true);receipt.putInt("boot",7);
            receipt.putString("phone_number","must_never_be_saved");
            check((Boolean)save.invoke(null,context,receipt),"initial receipt saved");
            Bundle restored=(Bundle)read.invoke(null,context);check(restored.getBoolean("hook"),"receipt roundtrip");
            long at=restored.getLong("observed_at");byte[] first=file.readFully();
            check(!new String(first,StandardCharsets.UTF_8).contains("must_never_be_saved"),"unknown private fields dropped");
            check((Boolean)save.invoke(null,context,receipt),"duplicate accepted");
            check(java.util.Arrays.equals(first,file.readFully()),"duplicate does not rewrite timestamp or disk");
            check(((Bundle)read.invoke(null,context)).getLong("observed_at")==at,"observation time retained");
            FileOutputStream interrupted=file.startWrite();interrupted.write("incomplete".getBytes(StandardCharsets.UTF_8));file.failWrite(interrupted);
            check(((Bundle)read.invoke(null,context)).getBoolean("hook"),"interrupted write retains previous receipt");
            check(!(Boolean)save.invoke(null,context,new Bundle()),"incomplete receipt rejected");
            receipt.putString("phase","failed");receipt.putBoolean("hook",false);save.invoke(null,context,receipt);
            check(!((Bundle)read.invoke(null,context)).getBoolean("hook"),"failure supersedes success");
            FileOutputStream damaged=file.startWrite();damaged.write("broken".getBytes(StandardCharsets.UTF_8));file.finishWrite(damaged);
            check(((Bundle)read.invoke(null,context)).isEmpty(),"damaged record remains unknown");
            result.putString("stream","HistoryInstrumentation: "+checks+" assertions passed\n");
        }catch(Throwable failed){result.putString("stream","FAILED: "+failed.toString()+"\n");result.putString("failure",failed.toString());}
        finally{
            file.delete();if(existed)try{FileOutputStream restored=file.startWrite();restored.write(original);file.finishWrite(restored);}catch(Throwable failure){result.putString("failure","history_restore_failed");}
        }
        finish(result.containsKey("failure")?-1:0,result);
    }
}
