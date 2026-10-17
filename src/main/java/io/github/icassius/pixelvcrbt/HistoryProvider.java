package io.github.icassius.pixelvcrbt;

import android.content.*;
import android.os.*;

/** Receipts can only be submitted by the scoped, privileged Dialer UID. Reads stay app-private. */
public final class HistoryProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public Bundle call(String method,String argument,Bundle extras){
        int caller=Binder.getCallingUid();
        if(caller==android.os.Process.myUid()&&"read".equals(method))return RuntimeHistory.read(getContext());
        if(!"record".equals(method))throw new SecurityException("unsupported history operation");
        getContext().enforceCallingPermission("android.permission.READ_PRIVILEGED_PHONE_STATE","privileged Dialer required");
        String[] packages=getContext().getPackageManager().getPackagesForUid(caller);
        boolean dialer=false;if(packages!=null)for(String name:packages)dialer|=RuntimeProbe.DIALER.equals(name);
        if(!dialer)throw new SecurityException("unexpected history sender");
        Bundle result=new Bundle();result.putBoolean("saved",RuntimeHistory.save(getContext(),extras));return result;
    }
    public android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String order){throw new SecurityException("unsupported");}
    public String getType(android.net.Uri uri){return null;}
    public android.net.Uri insert(android.net.Uri uri,ContentValues values){throw new SecurityException("unsupported");}
    public int update(android.net.Uri uri,ContentValues values,String selection,String[] args){throw new SecurityException("unsupported");}
    public int delete(android.net.Uri uri,String selection,String[] args){throw new SecurityException("unsupported");}
}
