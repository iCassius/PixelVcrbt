package io.github.icassius.pixelvcrbt;
public final class HistoryPolicyTest {
    static int checks;
    static void eq(String expected,String actual){checks++;if(!expected.equals(actual))throw new AssertionError(actual);}
    public static void main(String[] args){
        eq("none",HistoryPolicy.status("","a","m","m",1,1));
        eq("environment_changed",HistoryPolicy.status("a","b","m","m",1,1));
        eq("environment_changed",HistoryPolicy.status("a","a","m","n",1,1));
        eq("boot_changed",HistoryPolicy.status("a","a","m","m",1,2));
        eq("boot_changed",HistoryPolicy.status("a","a","m","m",-1,-1));
        eq("previous_observation",HistoryPolicy.status("a","a","m","m",1,1));
        System.out.println("HistoryPolicy: "+checks+" assertions passed");
    }
}
