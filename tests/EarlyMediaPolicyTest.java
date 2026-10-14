package com.antigravity.pixelvcrbt;
public final class EarlyMediaPolicyTest {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static EarlyMediaPolicy early(int route){EarlyMediaPolicy p=new EarlyMediaPolicy(true,0,route);p.observe(1,2,8);return p;}
    public static void main(String[] args){
        EarlyMediaPolicy p=early(1);
        check(p.isEarlyMedia(1,2),"voice to RX recognized");
        check(p.shouldRestore(true,true,1,2,8,9),"automatic speaker may restore once");
        check(!p.shouldRestore(false,true,1,2,8,9),"audio switch off");
        check(!p.shouldRestore(true,false,1,2,8,9),"unsafe target");
        check(!p.shouldRestore(true,true,4,2,8,9),"no active intervention");
        check(!p.shouldRestore(true,true,1,2,8,8),"no earpiece support");
        p.requested();check(!p.shouldRestore(true,true,1,2,8,9),"one shot including failure");
        p=early(1);p.userAudio();check(!p.shouldRestore(true,true,1,2,8,9),"user selection wins");
        for(int route:new int[]{0,2,4,8})check(!early(route).shouldRestore(true,true,1,2,8,15),"preserve other or unknown origin");
        p=new EarlyMediaPolicy(true,3,1);p.observe(1,2,8);check(!p.isEarlyMedia(1,2),"explicit video even before initial details update");
        p=early(1);p.userVideo();check(!p.isEarlyMedia(1,2),"video upgrade latches exclusion");
        p=early(1);p.observe(1,3,8);p.observe(1,2,8);check(!p.isEarlyMedia(1,2),"TX observed never reclassified");
        p=new EarlyMediaPolicy(false,0,1);p.observe(1,2,8);check(!p.isEarlyMedia(1,2),"unknown voice origin excluded");
        System.out.println("EarlyMediaPolicy: "+checks+" assertions passed");
    }
}
