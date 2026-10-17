package io.github.icassius.pixelvcrbt;

/** A bounded recovery decision, always preceded by reading the effective configuration. */
final class FallbackRecovery {
    static final long RETRY_DELAY=30000;
    static final int MAX_ATTEMPTS=2;
    final String phase, before;
    final int attempts;
    final long at;
    FallbackRecovery(String phase,int attempts,long at,String before){this.phase=phase;this.attempts=attempts;this.at=at;this.before=before;}
    static FallbackRecovery empty(){return new FallbackRecovery("none",0,0,"");}
    String decision(String actual,long now,boolean legacy){
        if(legacy&&attempts==0)return "legacy_attempt";
        if(attempts<0||attempts>=MAX_ATTEMPTS)return "attempt_limit";
        if(attempts==0)return "write";
        if("verified".equals(phase))return "changed_after_verified";
        if(!("prepared".equals(phase)||"submitted".equals(phase)||"uncertain".equals(phase)))return "invalid_record";
        if(!before.equals(actual))return "changed_by_provider";
        if(now<at||now-at<RETRY_DELAY)return "retry_wait";
        return "write";
    }
    FallbackRecovery prepare(String actual,long now){return new FallbackRecovery("prepared",attempts+1,now,actual);}
    FallbackRecovery phase(String next){return new FallbackRecovery(next,attempts,at,before);}
}
