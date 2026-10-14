package com.antigravity.pixelvcrbt;
import java.util.*;

/** Only the current video/transport prerequisite allowlist; no VoNR, CDMA or QoS tuning. */
public final class FallbackPlan {
    public static final String[] KEYS={"carrier_vt_available_bool","imsvt.video_on_default_bearer_supported_bool",
        "vendor.ims.carrier_video_over_lte_bool","vendor.ims.carrier_video_over_wifi_bool"};
    public static Map<String,Boolean> missing(Map<String,Object> current){
        Map<String,Boolean> result=new LinkedHashMap<>();
        for(String key:KEYS)if(current.get(key)==null||Boolean.FALSE.equals(current.get(key)))result.put(key,true);
        return result;
    }
}
