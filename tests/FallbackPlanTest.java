package io.github.icassius.pixelvcrbt;
import java.util.*;
public final class FallbackPlanTest {
    static int n;static void check(boolean b,String name){n++;if(!b)throw new AssertionError(name);}
    public static void main(String[] args){
        Map<String,Object> values=new HashMap<>();for(String key:FallbackPlan.KEYS)values.put(key,true);
        check(FallbackPlan.missing(values).isEmpty(),"provided policy never written");
        values.put(FallbackPlan.KEYS[1],false);Map<String,Boolean> patch=FallbackPlan.missing(values);
        check(patch.size()==1&&patch.containsKey(FallbackPlan.KEYS[1]),"false prerequisite alone patched");
        values.remove(FallbackPlan.KEYS[2]);check(FallbackPlan.missing(values).size()==2,"missing prerequisite detected");
        values.put("vonr_enabled_bool",false);values.put("support_cdma_1x_voice_calls_bool",false);
        check(!FallbackPlan.missing(values).containsKey("vonr_enabled_bool"),"VoNR excluded");
        check(!FallbackPlan.missing(values).containsKey("support_cdma_1x_voice_calls_bool"),"CDMA excluded");
        check(!FallbackPlan.missing(values).containsKey("imsvt.video_qos_precondition_supported_bool"),"QoS tuning excluded");
        check(FallbackPlan.missing(Collections.emptyMap()).size()==4,"empty safe allowlist");
        values.put(FallbackPlan.KEYS[3],7);check(!FallbackPlan.missing(values).containsKey(FallbackPlan.KEYS[3]),"unexpected existing type never overwritten");
        System.out.println("FallbackPlan: "+n+" assertions passed");
    }
}
