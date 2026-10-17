package io.github.icassius.pixelvcrbt;
public final class DetectionStatusTest {
    private static int checks;
    private static void check(String actual,String expected){checks++;if(!actual.equals(expected))throw new AssertionError(actual);}
    public static void main(String[] args){
        check(DetectionStatus.setting(true),"已开启");check(DetectionStatus.setting(false),"已关闭");check(DetectionStatus.setting(null),"无法读取");
        check(DetectionStatus.connection(true,true,false,false),"检测中");
        check(DetectionStatus.connection(false,true,false,false),"电话进程未连接");
        check(DetectionStatus.connection(false,true,true,true),"已加载");
        check(DetectionStatus.connection(false,true,true,false),"适配失败");
        check(DetectionStatus.connection(false,false,false,false),"需电话权限");
        check(DetectionStatus.ims(false,true,false,false,-1,false),"暂不可查询");
        check(DetectionStatus.ims(true,true,false,false,-1,false),"检测中");
        check(DetectionStatus.ims(false,true,true,true,2,false),"已注册");
        check(DetectionStatus.ims(false,true,true,true,0,false),"未注册");
        check(DetectionStatus.ims(false,true,true,true,-1,true),"检测中");
        check(DetectionStatus.ims(false,true,true,true,-1,false),"不可读取");
        check(DetectionStatus.ims(false,true,true,false,2,false),"已注册");
        check(DetectionStatus.connection(false,true,true,false,"probing",true),"正在适配");
        check(DetectionStatus.connection(false,true,true,false,"installing",true),"正在适配");
        check(DetectionStatus.connection(false,true,true,true,"ready",false),"需重新加载");
        check(DetectionStatus.connection(false,true,true,false,"degraded",true),"运行异常");
        System.out.println("DetectionStatus: "+checks+" assertions passed");
    }
}
