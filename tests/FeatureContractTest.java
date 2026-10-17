package io.github.icassius.pixelvcrbt;

import java.lang.reflect.Method;

public final class FeatureContractTest {
    private static int checks;
    static class Parent {public void callback(String value){} public static void staticOnly(String value){} public int wrongReturn(String value){return 0;}}
    static class Updated extends Parent {public void callback(Integer value){} public void callback(String value,int extra){}}
    abstract static class AbstractHost {public abstract void callback(String value);}
    interface Action {void callback(String value);}
    static class Host implements Action {public void callback(String value){}}
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    private static void rejects(Class<?> type,String name,Class<?> result,Class<?>...args)throws Exception{
        try{FeatureContract.instanceMethod(type,name,result,args);throw new AssertionError("accepted "+name);}catch(NoSuchMethodException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        Method inherited=FeatureContract.instanceMethod(Updated.class,"callback",void.class,String.class);
        check(inherited.getDeclaringClass()==Parent.class,"new unrelated overloads cannot replace inherited exact callback");
        check(FeatureContract.instanceMethod(Updated.class,"callback",void.class,Integer.class).getDeclaringClass()==Updated.class,"exact declared overload");
        rejects(Updated.class,"callback",void.class,Object.class);
        rejects(Updated.class,"wrongReturn",void.class,String.class);
        rejects(Updated.class,"staticOnly",void.class,String.class);
        rejects(AbstractHost.class,"callback",void.class,String.class);
        check(FeatureContract.subtype(Host.class,Action.class)==Host.class,"concrete subtype");
        try{FeatureContract.subtype(Parent.class,Action.class);throw new AssertionError("wrong host");}catch(IllegalArgumentException expected){checks++;}
        try{FeatureContract.subtype(AbstractHost.class,Object.class);throw new AssertionError("abstract host");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("FeatureContract: "+checks+" assertions passed");
    }
}
