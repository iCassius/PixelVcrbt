package com.antigravity.pixelvcrbt;
/** Settings are independent of a live host response. No response never means disabled. */
final class DetectionStatus {
    static String setting(Object value){return value instanceof Boolean?((Boolean)value?"已开启":"已关闭"):"无法读取";}
    static String connection(boolean checking,boolean permission,boolean response,boolean hook){
        if(!permission)return "需电话权限";
        if(!response)return checking?"检测中":"电话进程未连接";
        return hook?"已加载":"适配失败";
    }
    static String ims(boolean checking,boolean permission,boolean response,boolean hook,int state,boolean pending){
        if(!permission)return "需电话权限";
        if(!response)return checking?"检测中":"暂不可查询";
        if(!hook)return "暂不可查询";
        if(state==2)return "已注册";if(state==1)return "注册中";if(state==0)return "未注册";
        return pending?"检测中":"不可读取";
    }
}
