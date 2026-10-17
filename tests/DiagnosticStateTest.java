package io.github.icassius.pixelvcrbt;

public final class DiagnosticStateTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        DiagnosticState state=new DiagnosticState();state.begin(1);
        check(state.record(1,DiagnosticState.Step.VIDEO_PROVIDER,DiagnosticState.State.FAILED,"video_request_signature",100),"first failure recorded");
        DiagnosticState.Entry first=state.lastFailure();
        state.begin(1);check(state.current(DiagnosticState.Step.VIDEO_PROVIDER)==first,"repeated focus does not clear current diagnostics");
        check(!state.record(1,DiagnosticState.Step.VIDEO_PROVIDER,DiagnosticState.State.FAILED,"video_request_signature",200),"repeated polling does not replace failure time");
        check(state.lastFailure().at==100,"original observation time retained");
        state.end(1);
        check(!state.hasCurrentFailure(),"ended session does not masquerade as current failure");
        check(state.current(DiagnosticState.Step.VIDEO_PROVIDER)==null,"old session cannot claim current readiness");
        check(state.lastFailure()==first&&state.describe(1100).contains("历史记录"),"idle cannot erase history");
        check(!state.record(1,DiagnosticState.Step.REMOTE_VIEW,DiagnosticState.State.READY,"late_frame",1200),"late callbacks ignored after end");
        state.begin(2);
        check(!state.record(1,DiagnosticState.Step.AUDIO_RESTORE,DiagnosticState.State.FAILED,"late_error",1300),"previous call cannot corrupt new call");
        state.record(2,DiagnosticState.Step.VIDEO_PROVIDER,DiagnosticState.State.READY,"provider_callbacks_ready",1400);
        check(state.lastFailure()==first,"successful new session does not erase historical failure");
        check(state.current(DiagnosticState.Step.VIDEO_PROVIDER).scope==2,"new facts associated with new call");
        state.record(0,DiagnosticState.Step.MODULE_SWITCH,DiagnosticState.State.FAILED,"property_read:SecurityException",1500);
        check(state.hasCurrentFailure(),"global property failure is visible");
        state.end(2);check(state.hasCurrentFailure(),"global failure does not depend on call lifetime");
        state.record(0,DiagnosticState.Step.MODULE_SWITCH,DiagnosticState.State.READY,"enabled",1600);
        check(!state.hasCurrentFailure(),"read recovery clears only live failure");
        check(state.lastFailure().at==1500,"historical property failure retained");
        try{state.record(0,DiagnosticState.Step.MODULE_SWITCH,DiagnosticState.State.FAILED,"raw private message",1700);throw new AssertionError("unstructured data accepted");}catch(IllegalArgumentException expected){checks++;}
        check(Boolean.FALSE.equals(SwitchValue.parse("1",true)),"disabled property is explicit false");
        check(Boolean.TRUE.equals(SwitchValue.parse("0",true)),"disabled property inverted");
        check(SwitchValue.parse("unknown",false)==null,"invalid property remains unreadable");
        check(SwitchValue.parse(null,false)==null,"failed property remains unreadable");
        state.begin(3);state.record(3,DiagnosticState.Step.AUDIO_STATE,DiagnosticState.State.FAILED,"audio_state_read:SecurityException",1800);
        state.record(3,DiagnosticState.Step.AUDIO_STATE,DiagnosticState.State.READY,"audio_route_observed",1900);
        check(!state.hasCurrentFailure(),"audio read recovery is independent from restoration status");
        check(state.lastFailure().at==1800,"read recovery retains history without claiming current failure");
        System.out.println("DiagnosticState: "+checks+" assertions passed");
    }
}
