package io.github.icassius.pixelvcrbt;

import java.util.LinkedHashSet;
import java.util.Set;

/** Single-threaded async request ownership; late callbacks cannot finish a newer query. */
final class ImsQueries {
    static final long TIMEOUT=5000;
    static final class Ticket {
        final int sid;
        final long deadline;
        final Set<String> nonces=new LinkedHashSet<>();
        Ticket(int sid,String nonce,long now){this.sid=sid;deadline=now+TIMEOUT;nonces.add(nonce);}
        boolean matches(int subscription,long now){return sid==subscription&&now<deadline;}
    }
    static final class Completion {
        final String[] nonces;
        final int state;
        Completion(Ticket request,int result){nonces=request.nonces.toArray(new String[0]);state=result;}
    }
    private Ticket current;
    Ticket current(){return current;}
    Ticket begin(int sid,String nonce,long now){
        if(current!=null)throw new IllegalStateException("query already pending");
        current=new Ticket(sid,nonce,now);return current;
    }
    boolean join(String nonce){
        if(current==null)return false;
        if(current.nonces.contains(nonce))return true;
        return current.nonces.size()<16&&current.nonces.add(nonce);
    }
    Completion finish(Ticket request,int state,long now){
        if(request==null||current!=request)return null;
        current=null;
        return new Completion(request,now<request.deadline?state:-1);
    }
}
