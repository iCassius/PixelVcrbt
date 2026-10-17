package io.github.icassius.pixelvcrbt;

import android.telecom.InCallService;

public final class AudioFeaturesTest {
    static class Host extends InCallService {}
    static class Missing {}
    static class Legacy {public void route(int value){}}
    static class NewOverload extends Legacy {public void route(String value){}}
    static class WrongReturn {public int route(int value){return 0;}}
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        AudioFeatures features=AudioFeatures.probe(Host.class);
        check(features.endpoint&&features.legacy&&features.safe,"real SDK supports independent modern and legacy paths");
        check(features.requests.length==3,"all actual public audio request paths observed");
        check(features.endpointCallbacks.length==2,"captures actual available/current callbacks");
        check(features.endpointCallbacks[0].getName().equals("onAvailableCallEndpointsChanged"),"uses callback rather than nonexistent endpoint-list getter");
        boolean[] safe={true};
        check(AudioFeatures.optional(Missing.class,"route",void.class,safe,int.class)==null&&safe[0],"absent API is optional");
        check(AudioFeatures.optional(Legacy.class,"route",void.class,safe,int.class)!=null&&safe[0],"known complete legacy signature");
        AudioFeatures.optional(NewOverload.class,"route",void.class,safe,int.class);check(!safe[0],"unknown request overload disables automatic control");
        safe[0]=true;AudioFeatures.optional(WrongReturn.class,"route",void.class,safe,int.class);check(!safe[0],"changed return is unsafe");
        System.out.println("AudioFeatures: "+checks+" assertions passed");
    }
}
