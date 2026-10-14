package com.antigravity.pixelvcrbt;

/** Evidence labels never equate registration or RX state with confirmed SIP/RTP handshake. */
final class RuntimeReason {
    static String reason(boolean hook,int ims,int calls,boolean eligible,boolean early,boolean surface,boolean frames){
        if(!hook)return "未收到模块响应：检查 LSPosed 已启用、仅勾选电话并重启电话进程；电话未加载也会无响应";
        if(ims==0)return "IMS 未注册：检查 SIM、VoLTE／VoWiFi、运营商开通及上游配置";
        if(calls==0)return "模块已加载；请以语音呼叫测试视频彩铃，待机无法验证视频媒体";
        if(!eligible)return "当前呼叫不属于彩铃适配范围：仅目标卡、单路普通语音呼出";
        if(!early)return "尚未观察到接听前的接收视频会话：检查对端彩铃、网络及上游视频策略";
        if(frames)return "本次彩铃已观察到画面更新；不代表接通后的通话质量已验证";
        if(!surface)return "接收视频会话已出现，显示尚未绑定；保持电话通话界面在前台";
        return "显示已绑定但尚无画面更新：需检查上游媒体接收、解码或显示链路";
    }
}
