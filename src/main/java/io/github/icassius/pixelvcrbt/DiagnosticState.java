package io.github.icassius.pixelvcrbt;

import java.util.EnumMap;

/** Process-local facts. An idle event never erases a failure or turns it into live success. */
final class DiagnosticState {
    enum Step { MODULE_SWITCH, AUDIO_SWITCH, AUDIO_STATE, VIDEO_PROVIDER, REMOTE_VIEW, AUDIO_RESTORE }
    enum State { NOT_CHECKED, WAITING, READY, DISABLED, REQUESTED, ACCEPTED, CONFIRMED, CANCELLED, FAILED }
    static final class Entry {
        final Step step;final State state;final String reason;final long scope,at;
        Entry(Step step,State state,String reason,long scope,long at){this.step=step;this.state=state;this.reason=reason;this.scope=scope;this.at=at;}
    }
    private final EnumMap<Step,Entry> entries=new EnumMap<>(Step.class);
    private Entry failure;
    private long session;
    private boolean active;
    synchronized void begin(long token){
        if(active&&session==token)return;
        session=token;active=true;
        entries.remove(Step.AUDIO_STATE);entries.remove(Step.VIDEO_PROVIDER);entries.remove(Step.REMOTE_VIEW);entries.remove(Step.AUDIO_RESTORE);
    }
    synchronized void end(long token){if(token==session)active=false;}
    synchronized boolean record(long token,Step step,State state,String reason,long now){
        if(token!=0&&(!active||token!=session))return false;
        if(reason==null||!reason.matches("[A-Za-z0-9_:.]{1,96}"))throw new IllegalArgumentException("diagnostic reason must be a fixed code");
        Entry old=entries.get(step);
        if(old!=null&&old.scope==token&&old.state==state&&old.reason.equals(reason))return false;
        Entry entry=new Entry(step,state,reason,token,now);entries.put(step,entry);
        if(state==State.FAILED)failure=entry;
        return true;
    }
    synchronized Entry current(Step step){Entry e=entries.get(step);return e!=null&&(e.scope==0||active&&e.scope==session)?e:null;}
    synchronized Entry lastFailure(){return failure;}
    synchronized boolean hasCurrentFailure(){for(Step step:Step.values()){Entry e=current(step);if(e!=null&&e.state==State.FAILED)return true;}return false;}
    synchronized String describe(long now){
        StringBuilder text=new StringBuilder(active?"当前有会话；下列为实时检查结果":"当前无活动会话；业务步骤尚未检查");
        for(Step step:Step.values()){
            Entry e=current(step);text.append('\n').append(label(step)).append(": ");
            if(e==null)text.append("未检查");else text.append(e.state).append(" / ").append(e.reason);
        }
        if(failure!=null)text.append("\n最近一次失败（历史记录）：").append(label(failure.step)).append(" / ").append(failure.reason)
            .append(" / ").append(Math.max(0,now-failure.at)/1000).append(" 秒前");
        return text.toString();
    }
    private static String label(Step step){
        switch(step){case MODULE_SWITCH:return "模块开关读取";case AUDIO_SWITCH:return "音频开关读取";case AUDIO_STATE:return "当前音频读取";case VIDEO_PROVIDER:return "视频对象";case REMOTE_VIEW:return "远端视图";default:return "音频恢复";}
    }
}
