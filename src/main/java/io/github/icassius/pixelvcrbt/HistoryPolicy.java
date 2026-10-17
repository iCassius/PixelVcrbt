package io.github.icassius.pixelvcrbt;

/** Durable receipts describe an observation; they never grant current hook readiness. */
final class HistoryPolicy {
    static String status(String oldEnvironment,String environment,String oldModule,String module,int oldBoot,int boot){
        if(oldEnvironment==null||oldEnvironment.isEmpty())return "none";
        if(!oldModule.equals(module)||!oldEnvironment.equals(environment))return "environment_changed";
        if(oldBoot<0||boot<0||oldBoot!=boot)return "boot_changed";
        return "previous_observation";
    }
}
