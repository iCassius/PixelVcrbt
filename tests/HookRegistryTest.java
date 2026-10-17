package io.github.icassius.pixelvcrbt;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class HookRegistryTest {
    static class Parent { public void callback(){} }
    static class ChildA extends Parent {} static class ChildB extends Parent {}
    static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        HookRegistry<Method> hooks=new HookRegistry<>();AtomicInteger installs=new AtomicInteger(),removes=new AtomicInteger();
        Method first=ChildA.class.getMethod("callback"),second=ChildB.class.getMethod("callback");
        HookRegistry<Method>.Lease a=hooks.acquire(first,()->{installs.incrementAndGet();return removes::incrementAndGet;});
        HookRegistry<Method>.Lease b=hooks.acquire(second,()->{throw new AssertionError("duplicate method installation");});
        check(installs.get()==1);a.unhook();a.unhook();check(removes.get()==0);b.unhook();check(removes.get()==1);
        hooks.acquire(first,()->{installs.incrementAndGet();return removes::incrementAndGet;}).unhook();check(installs.get()==2);
        HookRegistry<String> failed=new HookRegistry<>();HookRegistry<String>.Lease bad=failed.acquire("bad",()->()->{throw new IllegalStateException();});
        try{bad.unhook();throw new AssertionError();}catch(IllegalStateException expected){check(true);}
        try{failed.acquire("bad",()->()->{});throw new AssertionError();}catch(IllegalStateException expected){check(true);}
        HookInstallation state=new HookInstallation();ExecutorService pool=Executors.newFixedThreadPool(8);AtomicInteger starts=new AtomicInteger();
        CountDownLatch go=new CountDownLatch(1);java.util.List<Future<?>> futures=new java.util.ArrayList<>();
        for(int n=0;n<32;n++)futures.add(pool.submit(()->{try{go.await();if(state.begin())starts.incrementAndGet();}catch(InterruptedException e){throw new RuntimeException(e);}}));
        go.countDown();for(Future<?> task:futures)task.get();pool.shutdown();check(starts.get()==1);
        System.out.println("HookRegistry: "+checks+" assertions passed");
    }
}
