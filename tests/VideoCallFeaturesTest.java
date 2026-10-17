package io.github.icassius.pixelvcrbt;

import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.view.Surface;

/** Runs against the Android SDK, without creating or invoking a stub Android object. */
public final class VideoCallFeaturesTest {
    static class AppProvider {
        public void setDisplaySurface(Surface surface){}
        public void sendSessionModifyRequest(VideoProfile profile){}
        public void sendSessionModifyRequest(VideoProfile from,VideoProfile to){}
    }
    static class BinderOnly {
        public void setDisplaySurface(Surface surface){}
        public void sendSessionModifyRequest(VideoProfile from,VideoProfile to){}
    }
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        VideoCallFeatures result=VideoCallFeatures.probe(AppProvider.class);
        check(result.modify.getParameterCount()==1,"app-layer hook never selects Binder overload");
        check(result.modify.getReturnType()==void.class,"request return type");
        check(InCallService.VideoCall.class.getMethod(result.modify.getName(),result.modify.getParameterTypes()).getReturnType()==void.class,"real Android SDK accepts selected request signature");
        check(InCallService.VideoCall.class.getMethod(result.display.getName(),result.display.getParameterTypes()).getReturnType()==void.class,"real Android SDK accepts selected display signature");
        try{VideoCallFeatures.probe(BinderOnly.class);throw new AssertionError("Binder-only object accepted");}catch(NoSuchMethodException expected){checks++;}
        System.out.println("VideoCallFeatures: "+checks+" assertions passed");
    }
}
