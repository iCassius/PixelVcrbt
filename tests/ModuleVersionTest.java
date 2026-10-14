package com.antigravity.pixelvcrbt;
public final class ModuleVersionTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        check(ModuleVersion.parse("v2.3.3-experimental").name.equals("2.3.3-experimental"));
        check(ModuleVersion.parse("2.10.0").compareTo(ModuleVersion.parse("2.9.0"))>0);
        check(ModuleVersion.parse("2.3.3").compareTo(ModuleVersion.parse("2.3.3-experimental"))>0);
        check(ModuleVersion.parse("2.3.3-rc.10").compareTo(ModuleVersion.parse("2.3.3-rc.2"))>0);
        check(ModuleVersion.parse("2.3.3-alpha").compareTo(ModuleVersion.parse("2.3.3-alpha.1"))<0);
        check(ModuleVersion.parse("2.3.3-1").compareTo(ModuleVersion.parse("2.3.3-alpha"))<0);
        check(ModuleVersion.parse("2.3.3-experimental").accepts(ModuleVersion.parse("2.3.4-experimental")));
        check(ModuleVersion.parse("2.3.3-experimental").accepts(ModuleVersion.parse("2.3.3")));
        check(!ModuleVersion.parse("2.3.3").accepts(ModuleVersion.parse("2.3.4-experimental")));
        check(ModuleVersion.parse("2.3.3").compareTo(ModuleVersion.parse("v2.3.3"))==0);
        check(ModuleVersion.parse(null)==null);check(ModuleVersion.parse("2.3")==null);
        check(ModuleVersion.parse("2.03.3")==null);check(ModuleVersion.parse("2.3.3-rc.01")==null);
        check(ModuleVersion.parse("https://example.com")==null);
        System.out.println("ModuleVersion: "+checks+" assertions passed");
    }
}
