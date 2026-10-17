package io.github.icassius.pixelvcrbt;

import android.bluetooth.BluetoothDevice;
import android.telecom.InCallService;
import android.telecom.CallAudioState;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;

final class AudioFeatures {
    final Method[] requests,endpointCallbacks;
    final boolean legacy,endpoint,safe;
    final String reason;
    private AudioFeatures(List<Method> requests,Method[] callbacks,boolean legacy,boolean endpoint,boolean safe){
        this.requests=requests.toArray(new Method[0]);endpointCallbacks=callbacks;this.legacy=legacy;this.endpoint=endpoint;this.safe=safe;
        reason=!safe?"audio_request_contract_changed":legacy||endpoint?"ready":"no_audio_backend";
    }
    static AudioFeatures probe(Class<?> service){
        try{return inspect(service);}
        catch(SecurityException|LinkageError unavailable){return new AudioFeatures(Collections.emptyList(),new Method[0],false,false,false);}
    }
    private static AudioFeatures inspect(Class<?> service){
        List<Method> requests=new ArrayList<>();boolean[] safe={true};Method[] callbacks=new Method[0];
        Method legacy=optional(InCallService.class,"setAudioRoute",void.class,safe,int.class);
        boolean legacyBackend=false;
        if(legacy!=null)try{FeatureContract.instanceMethod(InCallService.class,"getCallAudioState",CallAudioState.class);legacyBackend=true;}
        catch(ReflectiveOperationException|LinkageError unavailable){/* Setter alone cannot confirm a route. */}
        Method bluetooth=optional(InCallService.class,"requestBluetoothAudio",void.class,safe,BluetoothDevice.class);
        if(legacy!=null)requests.add(legacy);if(bluetooth!=null)requests.add(bluetooth);
        boolean endpoint=false;
        try{
            Class<?> type=Class.forName("android.telecom.CallEndpoint",false,InCallService.class.getClassLoader());
            Class<?> outcome=Class.forName("android.os.OutcomeReceiver",false,InCallService.class.getClassLoader());
            Method request=optional(InCallService.class,"requestCallEndpointChange",void.class,safe,type,Executor.class,outcome);
            if(request!=null){
                requests.add(request);
                try{
                    FeatureContract.instanceMethod(InCallService.class,"getCurrentCallEndpoint",type);
                    FeatureContract.instanceMethod(type,"getEndpointType",int.class);
                    callbacks=new Method[]{FeatureContract.instanceMethod(service,"onAvailableCallEndpointsChanged",void.class,List.class),
                        FeatureContract.instanceMethod(service,"onCallEndpointChanged",void.class,type)};
                    endpoint=true;
                }catch(ReflectiveOperationException|LinkageError unavailable){callbacks=new Method[0];}
            }
        }catch(ClassNotFoundException unavailable){/* Legacy-only platform. */}
        catch(LinkageError unavailable){safe[0]=false;}
        return new AudioFeatures(requests,callbacks,legacyBackend,endpoint,safe[0]);
    }
    // Missing public APIs are optional; an existing but unfamiliar request shape is unsafe to overlook.
    static Method optional(Class<?> type,String name,Class<?> returns,boolean[] safe,Class<?>...parameters){
        try{
            for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method method:c.getDeclaredMethods()){
                if(method.getName().equals(name)&&Modifier.isPublic(method.getModifiers())&&
                    (!Arrays.equals(method.getParameterTypes(),parameters)||method.getReturnType()!=returns||Modifier.isStatic(method.getModifiers())))safe[0]=false;
            }
            return FeatureContract.instanceMethod(type,name,returns,parameters);
        }catch(NoSuchMethodException unavailable){
            for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method method:c.getDeclaredMethods())if(method.getName().equals(name)&&Modifier.isPublic(method.getModifiers()))safe[0]=false;
            return null;
        }
    }
}
