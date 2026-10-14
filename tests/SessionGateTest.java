package com.antigravity.pixelvcrbt;

public final class SessionGateTest {
    private static int checks;
    private static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    public static void main(String[] args) {
        SessionGate gate=new SessionGate(true);Object p=new Object(),s=new Object();
        long first=gate.update(p,s);
        check(gate.canBind(first,true,true,1,2,true),"RX early media binds");
        check(!gate.canBind(first,true,true,4,2,true),"active hands off");
        check(!gate.canBind(first,true,true,1,3,true),"bidirectional excluded");
        check(!gate.canBind(first,true,true,1,6,true),"paused RX excluded");
        check(!gate.canBind(first,true,true,1,0,true),"voice untouched");
        check(!gate.canBind(first,false,true,1,2,true),"kill switch");
        check(!gate.canBind(first,true,false,1,2,true),"unsafe call excluded");
        check(!gate.canBind(first,true,true,1,2,false),"hidden surface excluded");
        gate.submitted();check(!gate.canBind(first,true,true,1,2,true),"idempotent pair");
        Object replacement=new Object();long second=gate.update(p,replacement);
        check(!gate.canBind(first,true,true,1,2,true),"stale surface generation rejected");
        check(gate.canBind(second,true,true,1,2,true),"recreated surface rebinds");
        gate.submitted();long third=gate.update(new Object(),replacement);
        check(!gate.canBind(second,true,true,1,2,true),"stale provider generation rejected");
        check(gate.canBind(third,true,true,1,2,true),"new provider allowed");
        gate.stop();check(!gate.canBind(third,true,true,1,2,true),"stopped never rebinds");
        SessionGate videoOrigin=new SessionGate(false);long token=videoOrigin.update(p,s);
        check(!videoOrigin.canBind(token,true,true,1,2,true),"actual video origin excluded");
        SessionGate reordered=new SessionGate(true);long waiting=reordered.update(null,s);
        check(!reordered.canBind(waiting,true,true,1,2,true),"surface before provider waits");
        long ready=reordered.update(p,s);check(reordered.canBind(ready,true,true,1,2,true),"late provider wakes");
        System.out.println("SessionGate: "+checks+" assertions passed");
    }
}
