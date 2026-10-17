package io.github.icassius.pixelvcrbt;

import android.telecom.*;
import android.os.OutcomeReceiver;
import java.util.*;

/** Available endpoints come from the actual service callback, never from constructed endpoints. */
final class CallEndpointRouter {
    private static final Map<InCallService,List<CallEndpoint>> available=Collections.synchronizedMap(new WeakHashMap<>());
    static void remember(InCallService service,List<?> supplied){
        if(supplied==null){available.remove(service);return;}
        List<CallEndpoint> copy=new ArrayList<>();
        for(Object endpoint:supplied){if(!(endpoint instanceof CallEndpoint)){available.remove(service);return;}copy.add((CallEndpoint)endpoint);}
        available.put(service,Collections.unmodifiableList(copy));
    }
    static boolean observed(InCallService service){return available.containsKey(service);}
    static CallEndpoint earpiece(InCallService service){return EndpointSelection.unique(available.get(service),e->e.getEndpointType()==CallEndpoint.TYPE_EARPIECE);}
    static int route(InCallService service){
        CallEndpoint current=service.getCurrentCallEndpoint();if(current==null)return 0;
        switch(current.getEndpointType()){
            case CallEndpoint.TYPE_EARPIECE:return CallAudioState.ROUTE_EARPIECE;
            case CallEndpoint.TYPE_SPEAKER:return CallAudioState.ROUTE_SPEAKER;
            case CallEndpoint.TYPE_BLUETOOTH:return CallAudioState.ROUTE_BLUETOOTH;
            case CallEndpoint.TYPE_WIRED_HEADSET:return CallAudioState.ROUTE_WIRED_HEADSET;
            default:return 0;
        }
    }
    static void request(InCallService service,CallEndpoint selected,OutcomeReceiver<Void,CallEndpointException> callback){
        List<CallEndpoint> latest=available.get(service);
        if(selected==null||latest==null||!latest.contains(selected))throw new IllegalStateException("endpoint_list_changed");
        service.requestCallEndpointChange(selected,service.getMainExecutor(),callback);
    }
}
