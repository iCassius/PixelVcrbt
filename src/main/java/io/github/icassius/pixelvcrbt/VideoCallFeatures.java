package io.github.icassius.pixelvcrbt;

import android.telecom.VideoProfile;
import android.view.Surface;
import java.lang.reflect.Method;

/** InCallService.VideoCall is the app API; the Binder IVideoProvider has a different request signature. */
final class VideoCallFeatures {
    static final class Missing extends NoSuchMethodException {
        final String reason;
        Missing(String reason){super(reason);this.reason=reason;}
    }
    final Method display,modify;
    private VideoCallFeatures(Method display,Method modify){this.display=display;this.modify=modify;}
    static VideoCallFeatures probe(Class<?> implementation)throws NoSuchMethodException{
        Method display,modify;
        try{display=FeatureContract.instanceMethod(implementation,"setDisplaySurface",void.class,Surface.class);}
        catch(NoSuchMethodException missing){throw new Missing("display_surface_signature");}
        try{modify=FeatureContract.instanceMethod(implementation,"sendSessionModifyRequest",void.class,VideoProfile.class);}
        catch(NoSuchMethodException missing){throw new Missing("video_request_signature");}
        return new VideoCallFeatures(display,modify);
    }
}
