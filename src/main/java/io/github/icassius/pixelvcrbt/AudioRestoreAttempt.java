package io.github.icassius.pixelvcrbt;

/** One request per session, including failure. Async acceptance is not route confirmation. */
final class AudioRestoreAttempt {
    enum State { IDLE, REQUESTED, ACCEPTED, CONFIRMED, FAILED, TIMED_OUT, CANCELLED }
    private State state=State.IDLE;
    private long generation,deadline;
    private String backend="none",reason="not_requested";
    long begin(String selected,long now){
        if(state!=State.IDLE)return -1;
        backend=selected;state=State.REQUESTED;reason="request_sent";deadline=now+5000;return ++generation;
    }
    boolean result(long token,boolean success,String error,long now){
        tick(now);if(token!=generation||state!=State.REQUESTED)return false;
        state=success?State.ACCEPTED:State.FAILED;reason=success?"awaiting_route":error;return true;
    }
    boolean observe(long token,boolean earpiece,long now){
        tick(now);if(token!=generation||!pending()||!earpiece)return false;
        state=State.CONFIRMED;reason="earpiece_observed";return true;
    }
    void tick(long now){if(pending()&&now>=deadline){state=State.TIMED_OUT;reason="route_confirmation_timeout";}}
    void cancel(){if(pending()||state==State.CONFIRMED){++generation;state=State.CANCELLED;reason="session_or_user_changed";}}
    private boolean pending(){return state==State.REQUESTED||state==State.ACCEPTED;}
    State state(){return state;}String backend(){return backend;}String reason(){return reason;}long token(){return generation;}
}
