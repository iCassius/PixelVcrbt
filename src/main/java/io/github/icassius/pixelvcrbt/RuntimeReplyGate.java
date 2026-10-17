package io.github.icassius.pixelvcrbt;

/** UI-local monotonic deadline and per-process sequence; no persisted health result. */
final class RuntimeReplyGate {
    static final int SCHEMA=2;
    private String nonce,instance;
    private long deadline,sequence=-1;
    static boolean validNonce(String value){return value!=null&&value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    void begin(String value,long now,long timeout){nonce=value;deadline=now+timeout;instance=null;sequence=-1;}
    boolean current(String value,long now){return nonce!=null&&nonce.equals(value)&&now<deadline;}
    boolean accept(String value,int schema,String process,long seq,long now){
        if(!current(value,now)||schema!=SCHEMA||!validNonce(process)||seq<0)return false;
        if(instance!=null&&!instance.equals(process))return false;
        if(seq<=sequence)return false;
        instance=process;sequence=seq;return true;
    }
    void clear(){nonce=null;instance=null;sequence=-1;}
}
