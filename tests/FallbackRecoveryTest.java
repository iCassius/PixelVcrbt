package io.github.icassius.pixelvcrbt;
public final class FallbackRecoveryTest {
    static int checks;
    static void eq(String expected,String actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        FallbackRecovery r=FallbackRecovery.empty();
        eq("write",r.decision("0010",100,false));
        eq("legacy_attempt",r.decision("0010",100,true));
        r=r.prepare("0010",100);
        for(String phase:new String[]{"prepared","submitted","uncertain"}){
            FallbackRecovery p=r.phase(phase);
            eq("retry_wait",p.decision("0010",30099,false));
            eq("retry_wait",p.decision("0010",0,false));
            eq("changed_by_provider",p.decision("1010",30100,false));
            eq("write",p.decision("0010",30100,false));
        }
        eq("changed_after_verified",r.phase("verified").decision("0010",40000,false));
        eq("invalid_record",r.phase("unknown").decision("0010",40000,false));
        eq("attempt_limit",r.prepare("0010",30100).decision("0010",90000,false));
        eq("attempt_limit",new FallbackRecovery("prepared",-1,0,"").decision("",90000,false));
        System.out.println("FallbackRecovery: "+checks+" assertions passed");
    }
}
