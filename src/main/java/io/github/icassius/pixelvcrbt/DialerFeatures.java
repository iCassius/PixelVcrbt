package io.github.icassius.pixelvcrbt;

import android.app.Activity;
import android.telecom.Call;
import android.telecom.InCallService;
import java.lang.reflect.Method;

/** Stable semantic anchors, exact signatures and assignability, never a version allowlist. */
final class DialerFeatures {
    static final String ADAPTER="incall-service-v1";
    final Class<?> host;
    final Method added,removed;
    final AudioFeatures audio;
    private DialerFeatures(Class<?> host,Method added,Method removed,AudioFeatures audio){
        this.host=host;this.added=added;this.removed=removed;this.audio=audio;
    }
    static DialerFeatures probe(ClassLoader loader)throws Exception{
        Class<?> service=FeatureContract.subtype(Class.forName("com.android.incallui.InCallServiceImpl",false,loader),InCallService.class);
        Class<?> host=FeatureContract.subtype(Class.forName("com.android.dialer.incall.activity.ui.InCallActivity",false,loader),Activity.class);
        Method added=FeatureContract.instanceMethod(service,"onCallAdded",void.class,Call.class);
        Method removed=FeatureContract.instanceMethod(service,"onCallRemoved",void.class,Call.class);
        return new DialerFeatures(host,added,removed,AudioFeatures.probe(service));
    }
}
