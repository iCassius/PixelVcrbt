package io.github.icassius.pixelvcrbt;

/** Call-local business intent and one-shot audio decision, independent of Android. */
public final class EarlyMediaPolicy {
    private final boolean voiceOrigin;
    private volatile boolean realVideo,userAudio;
    private boolean attempted,earlyMedia;
    private int originRoute;
    public EarlyMediaPolicy(boolean voiceOrigin,int requestedVideo,int initialRoute){
        this.voiceOrigin=voiceOrigin;realVideo=requestedVideo!=0;originRoute=initialRoute;
    }
    public void observe(int state,int video,int route){
        if((video&1)!=0)realVideo=true;
        if(!earlyMedia&&state==1&&video==0&&originRoute==0)originRoute=route;
        if(state==1&&video==2&&!realVideo&&voiceOrigin)earlyMedia=true;
    }
    public void userVideo(){realVideo=true;}
    public boolean userVideoRequested(){return realVideo;}
    public void userAudio(){userAudio=true;}
    public boolean isEarlyMedia(int state,int video){return voiceOrigin&&!realVideo&&state==1&&video==2;}
    public boolean shouldRestore(boolean enabled,boolean eligible,int state,int video,int route,int supported){
        return enabled&&eligible&&isEarlyMedia(state,video)&&earlyMedia&&!userAudio&&!attempted&&
            originRoute==1&&route==8&&(supported&1)!=0;
    }
    public void requested(){attempted=true;}
    public boolean attempted(){return attempted;}
}
