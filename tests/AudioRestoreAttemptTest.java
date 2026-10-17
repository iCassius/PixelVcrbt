package io.github.icassius.pixelvcrbt;

public final class AudioRestoreAttemptTest {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        AudioRestoreAttempt first=new AudioRestoreAttempt();first.cancel();
        check(first.state()==AudioRestoreAttempt.State.IDLE,"ordinary voice setup must not consume the later early-media attempt");
        long token=first.begin("endpoint",100);
        check(token>0&&first.state()==AudioRestoreAttempt.State.REQUESTED,"request pending");
        check(first.result(token,true,"",200),"system accepts request");
        check(first.state()==AudioRestoreAttempt.State.ACCEPTED,"accepted is not confirmed");
        check(!first.result(token,false,"duplicate",201),"second callback cannot reverse acceptance");
        check(!first.observe(token,false,210),"speaker observation does not confirm");
        check(first.observe(token,true,220),"earpiece observation confirms");
        check(first.begin("legacy",230)==-1,"no duplicate legacy request after modern request");
        first.cancel();check(first.state()==AudioRestoreAttempt.State.CANCELLED,"user choice supersedes prior restoration");
        check(!first.result(token,false,"late",240),"old result cannot override user selection");
        AudioRestoreAttempt cancelled=new AudioRestoreAttempt();long second=cancelled.begin("endpoint",0);cancelled.cancel();
        check(!cancelled.result(second,true,"",10),"user choice before callback");
        check(!cancelled.observe(second,true,11),"late route observation after cancellation");
        check(cancelled.begin("legacy",12)==-1,"no fallback after user choice");
        AudioRestoreAttempt failed=new AudioRestoreAttempt();long third=failed.begin("endpoint",0);
        check(failed.result(third,false,"endpoint_request_failed",1),"error stored");
        check(failed.begin("legacy",2)==-1,"error does not cause second transport request");
        check(!failed.observe(third,true,3),"unrelated later earpiece cannot turn failed request green");
        AudioRestoreAttempt silent=new AudioRestoreAttempt();long fourth=silent.begin("legacy",100);
        silent.tick(5099);check(silent.state()==AudioRestoreAttempt.State.REQUESTED,"waits within deadline");
        silent.tick(5100);check(silent.state()==AudioRestoreAttempt.State.TIMED_OUT,"no callback bounded by deadline");
        check(!silent.result(fourth,true,"",5101),"late success ignored");
        check(silent.begin("endpoint",5102)==-1,"timeout does not cause retry");
        EarlyMediaPolicy policy=new EarlyMediaPolicy(true,0,1);policy.observe(1,2,8);
        check(policy.mayControlAudio(1,2),"eligible voice early media");policy.userAudio();
        check(!policy.mayControlAudio(1,2),"explicit user audio permanently wins");
        EarlyMediaPolicy video=new EarlyMediaPolicy(true,0,1);video.userVideo();
        check(!video.mayControlAudio(1,2),"active video request stays native");
        check(!new EarlyMediaPolicy(true,0,1).mayControlAudio(4,2),"connected call stays native");
        System.out.println("AudioRestoreAttempt: "+checks+" assertions passed");
    }
}
