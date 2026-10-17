package io.github.icassius.pixelvcrbt;
/** Settings are independent of a live host response. No response never means disabled. */
final class DetectionStatus {
    static String setting(Object value){return value instanceof Boolean?((Boolean)value?"已开启":"已关闭"):"无法读取";}
    static String connection(boolean checking,boolean permission,boolean response,boolean hook){
        return connection(checking,permission,response,hook,"failed",true);
    }
    static String connection(boolean checking,boolean permission,boolean response,boolean hook,String phase,boolean current){
        if(!permission)return "需电话权限";
        if(!response)return checking?"检测中":"电话进程未连接";
        if(!current)return "需重新加载";
        if("degraded".equals(phase))return "运行异常";
        if("waiting".equals(phase)||"probing".equals(phase)||"installing".equals(phase))return "正在适配";
        return hook?"已加载":"适配失败";
    }
    static String ims(boolean checking,boolean permission,boolean response,boolean hook,int state,boolean pending){
        if(!permission)return "需电话权限";
        if(!response)return checking?"检测中":"暂不可查询";
        if(state==2)return "已注册";if(state==1)return "注册中";if(state==0)return "未注册";
        return pending?"检测中":"不可读取";
    }
}
