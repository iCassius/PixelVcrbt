package io.github.icassius.pixelvcrbt;

public final class ImsQueriesTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        ImsQueries query=new ImsQueries();ImsQueries.Ticket first=query.begin(1,"a",100);
        check(first.matches(1,200),"same subscription shares query");check(!first.matches(2,200),"subscription switch invalidates query");
        check(query.join("b"),"coalesced poll");check(query.join("b"),"duplicate nonce does not consume slot");
        ImsQueries.Completion good=query.finish(first,2,200);
        check(good.state==2&&good.nonces.length==2,"registration replies to both polls");
        check(query.finish(first,0,201)==null,"duplicate callback discarded");
        ImsQueries.Ticket second=query.begin(1,"c",300);
        check(query.finish(first,0,301)==null&&query.current()==second,"old callback cannot finish current request");
        check(!second.matches(1,5300),"deadline inclusive");
        check(query.finish(second,2,5300).state==-1,"late positive callback cannot claim registration");
        ImsQueries.Ticket silent=query.begin(1,"d",6000);
        check(query.finish(silent,-1,11000).state==-1&&query.current()==null,"no callback completes as unreadable at timeout");
        check(query.finish(silent,2,11001)==null,"callback after timeout ignored");
        ImsQueries.Ticket bounded=query.begin(1,"n0",12000);
        for(int i=1;i<16;i++)check(query.join("n"+i),"bounded subscriber accepted");
        check(!query.join("overflow"),"subscriber cap enforced");
        check(query.finish(bounded,0,12001).nonces.length==16,"all bounded subscribers complete");
        System.out.println("ImsQueries: "+checks+" assertions passed");
    }
}
