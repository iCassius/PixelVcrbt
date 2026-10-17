package io.github.icassius.pixelvcrbt;

public final class RuntimeReplyGateTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        String first="12345678-1234-1234-1234-123456789abc",next="22345678-1234-1234-1234-123456789abc",process="32345678-1234-1234-1234-123456789abc";
        RuntimeReplyGate gate=new RuntimeReplyGate();gate.begin(first,100,2500);
        check(!gate.accept(next,2,process,1,110),"wrong nonce");
        check(!gate.accept(first,1,process,1,110),"old protocol");
        check(!gate.accept(first,2,"untrusted-id",1,110),"invalid instance");
        check(gate.accept(first,2,process,4,110),"first current reply");
        check(!gate.accept(first,2,process,4,120),"duplicate");
        check(!gate.accept(first,2,process,3,120),"out of order");
        check(!gate.accept(first,2,next,5,120),"different process cannot merge into current request");
        check(gate.accept(first,2,process,5,130),"IMS result after entry reply");
        check(!gate.accept(first,2,process,6,2600),"deadline inclusive");
        gate.begin(next,3000,2500);
        check(!gate.accept(first,2,process,7,3001),"previous poll callback");
        check(gate.accept(next,2,first,1,3001),"new process accepted with new poll");
        gate.clear();check(!gate.accept(next,2,first,2,3002),"paused page discards result");
        check(!RuntimeReplyGate.validNonce(null),"null query rejected");check(!RuntimeReplyGate.validNonce("x"),"malformed query rejected");
        check(RuntimeReplyGate.validNonce(first),"UUID query accepted");
        System.out.println("RuntimeReplyGate: "+checks+" assertions passed");
    }
}
