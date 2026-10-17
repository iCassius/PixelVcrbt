package io.github.icassius.pixelvcrbt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exact structural contracts; package versions are never capability evidence. */
final class FeatureContract {
    static Method instanceMethod(Class<?> type,String name,Class<?> returns,Class<?>...parameters) throws NoSuchMethodException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()){
            try{
                Method method=c.getDeclaredMethod(name,parameters);
                if(method.getReturnType()!=returns||Modifier.isStatic(method.getModifiers())||Modifier.isAbstract(method.getModifiers()))
                    throw new NoSuchMethodException(type.getName()+"."+name+": incompatible implementation");
                return method;
            }catch(NoSuchMethodException missing){
                // A declaration with these parameters must not be bypassed for a parent implementation.
                for(Method method:c.getDeclaredMethods())if(method.getName().equals(name)&&java.util.Arrays.equals(method.getParameterTypes(),parameters))throw missing;
            }
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    static Class<?> subtype(Class<?> type,Class<?> expected){
        if(!expected.isAssignableFrom(type)||Modifier.isAbstract(type.getModifiers()))
            throw new IllegalArgumentException(type.getName()+": incompatible host");
        return type;
    }
}
