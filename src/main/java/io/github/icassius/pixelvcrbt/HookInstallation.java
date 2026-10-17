package io.github.icassius.pixelvcrbt;

import java.util.ArrayList;
import java.util.List;

/** One process, one attempt. Callback effects stay disabled until every required step commits. */
final class HookInstallation {
    private volatile String phase="waiting";
    private String step="application";
    private final List<Runnable> undo=new ArrayList<>();
    private final List<String> completed=new ArrayList<>();
    synchronized boolean begin(){if(!"waiting".equals(phase))return false;phase="probing";return true;}
    synchronized void installing(){phase="installing";}
    synchronized void step(String name){step=name;}
    synchronized void added(String name,Runnable cleanup){undo.add(cleanup);completed.add(name);}
    synchronized void commit(){if(!"installing".equals(phase))throw new IllegalStateException(phase);phase="ready";step="ready";}
    synchronized void fail(){
        phase="failed";
        for(int i=undo.size()-1;i>=0;i--)try{undo.get(i).run();}catch(Throwable ignored){}
        undo.clear();completed.clear();
    }
    synchronized boolean ready(){return "ready".equals(phase);}
    synchronized String phase(){return phase;}
    synchronized String step(){return step;}
    synchronized String[] completed(){return completed.toArray(new String[0]);}
}
