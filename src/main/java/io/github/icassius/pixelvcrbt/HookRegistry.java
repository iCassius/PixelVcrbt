package io.github.icassius.pixelvcrbt;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Deduplicate the actual declaring method, including methods shared by concrete subclasses. */
final class HookRegistry<K> {
    private final Map<K,Entry> entries=new HashMap<>();
    private static final class Entry { final Runnable cleanup;int owners;boolean poisoned;Entry(Runnable cleanup){this.cleanup=cleanup;} }
    final class Lease {
        private final K key;private final Entry entry;private boolean released;
        Lease(K key,Entry entry){this.key=key;this.entry=entry;}
        void unhook(){synchronized(HookRegistry.this){
            if(released)return;released=true;
            if(--entry.owners>0)return;
            try{entry.cleanup.run();entries.remove(key);}catch(Throwable failed){entry.poisoned=true;throw new IllegalStateException("hook_cleanup_failed",failed);}
        }}
    }
    synchronized Lease acquire(K key,Supplier<Runnable> install){
        Entry entry=entries.get(key);
        if(entry!=null&&entry.poisoned)throw new IllegalStateException("hook_cleanup_uncertain");
        if(entry==null){entry=new Entry(install.get());entries.put(key,entry);}
        entry.owners++;return new Lease(key,entry);
    }
}
