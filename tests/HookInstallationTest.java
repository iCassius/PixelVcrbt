package io.github.icassius.pixelvcrbt;

import java.util.ArrayList;
import java.util.List;

public final class HookInstallationTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args){
        // Inject a failure after each possible install position, including cleanup failure.
        for(int fault=0;fault<=8;fault++){
            HookInstallation attempt=new HookInstallation();List<Integer> removed=new ArrayList<>();
            check(attempt.begin(),"initial attempt");check(!attempt.begin(),"reentrant attempt blocked");attempt.installing();
            for(int i=0;i<fault;i++){
                final int step=i;attempt.step("step"+i);
                attempt.added("step"+i,()->{removed.add(step);if(step==2)throw new IllegalStateException("injected unhook failure");});
                check(!attempt.ready(),"partial installation cannot run callbacks");
            }
            attempt.fail();check(!attempt.ready(),"failure disables callbacks");
            check(removed.size()==fault,"all cleanup steps attempted");
            for(int i=0;i<fault;i++)check(removed.get(i)==fault-i-1,"reverse rollback");
            check(attempt.completed().length==0,"no successful-hook claim remains");check(!attempt.begin(),"failed process does not retry and duplicate hooks");
        }
        HookInstallation good=new HookInstallation();good.begin();good.installing();good.added("call",()->{});good.commit();
        check(good.ready(),"commit enables callbacks");check(good.completed().length==1,"committed evidence");check(!good.begin(),"ready process cannot reinstall");
        System.out.println("HookInstallation: "+checks+" assertions passed");
    }
}
