package com.antigravity.pixelvcrbt;

/** Android-free decision core, tested with reordered events and stale generations. */
public final class SessionGate {
    private long generation=1;
    private Object provider, surface, boundProvider, boundSurface;
    private boolean stopped, voiceOrigin;
    public SessionGate(boolean voiceOrigin) { this.voiceOrigin=voiceOrigin; }
    public long update(Object provider,Object surface) {
        if(this.provider!=provider || this.surface!=surface) {
            this.provider=provider;this.surface=surface;generation++;
        }
        return generation;
    }
    public boolean canBind(long token,boolean enabled,boolean eligible,int state,int video,boolean visible) {
        return !stopped && voiceOrigin && token==generation && enabled && eligible && state==1 && video==2 &&
                visible && provider!=null && surface!=null && (boundProvider!=provider || boundSurface!=surface);
    }
    public void submitted() {boundProvider=provider;boundSurface=surface;}
    public void stop(){stopped=true;generation++;}
    public long generation(){return generation;}
}
