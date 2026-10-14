package com.antigravity.pixelvcrbt;
public final class RuntimeReasonTest {
    private static int checks;
    private static void check(String text,String expected){checks++;if(!text.contains(expected))throw new AssertionError(text);}
    public static void main(String[] args){
        check(RuntimeReason.reason(false,2,0,false,false,false,false),"LSPosed");
        check(RuntimeReason.reason(true,0,0,false,false,false,false),"IMS 未注册");
        check(RuntimeReason.reason(true,2,0,false,false,false,false),"待机无法验证");
        check(RuntimeReason.reason(true,2,1,false,false,false,false),"适配范围");
        check(RuntimeReason.reason(true,2,1,true,false,false,false),"上游视频策略");
        check(RuntimeReason.reason(true,2,1,true,true,false,false),"前台");
        check(RuntimeReason.reason(true,2,1,true,true,true,false),"解码");
        check(RuntimeReason.reason(true,2,1,true,true,true,true),"画面更新");
        System.out.println("PASS: "+checks+" runtime reason assertions");
    }
}
