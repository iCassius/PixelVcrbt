package com.antigravity.pixelvcrbt;
import java.math.BigInteger;
import java.util.regex.*;

/** SemVer ordering; release age and lexical sorting do not decide upgrades. */
final class ModuleVersion implements Comparable<ModuleVersion> {
    final String name,pre;final BigInteger[] core;
    private ModuleVersion(String name,String pre,BigInteger[] core){this.name=name;this.pre=pre;this.core=core;}
    static ModuleVersion parse(String text){
        if(text==null)return null;
        Matcher m=Pattern.compile("^v?((0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?)$").matcher(text);
        if(!m.matches())return null;
        String pre=m.group(5);if(pre!=null)for(String s:pre.split("\\."))if(s.matches("[0-9]+")&&s.length()>1&&s.startsWith("0"))return null;
        return new ModuleVersion(m.group(1),pre,new BigInteger[]{new BigInteger(m.group(2)),new BigInteger(m.group(3)),new BigInteger(m.group(4))});
    }
    boolean accepts(ModuleVersion candidate){return pre!=null||candidate.pre==null;}
    public int compareTo(ModuleVersion other){
        for(int i=0;i<3;i++){int c=core[i].compareTo(other.core[i]);if(c!=0)return c;}
        if(pre==null)return other.pre==null?0:1;if(other.pre==null)return -1;
        String[] a=pre.split("\\."),b=other.pre.split("\\.");
        for(int i=0;i<Math.min(a.length,b.length);i++){
            boolean an=a[i].matches("[0-9]+"),bn=b[i].matches("[0-9]+");int c;
            if(an&&bn)c=new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
            else if(an!=bn)c=an?-1:1;else c=a[i].compareTo(b[i]);if(c!=0)return c;
        }return Integer.compare(a.length,b.length);
    }
}
