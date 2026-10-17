package io.github.icassius.pixelvcrbt;

import java.util.*;

public final class EndpointSelectionTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Endpoint {final boolean earpiece;Endpoint(boolean value){earpiece=value;}}
    public static void main(String[] args){
        Endpoint speaker=new Endpoint(false),earpiece=new Endpoint(true),bluetooth=new Endpoint(false);
        check(EndpointSelection.unique(Arrays.asList(speaker,earpiece,bluetooth),e->e.earpiece)==earpiece,"returns actual supplied object");
        check(EndpointSelection.unique(Arrays.asList(earpiece,new Endpoint(true)),e->e.earpiece)==null,"ambiguous earpiece refused");
        check(EndpointSelection.unique(Arrays.asList(speaker,bluetooth),e->e.earpiece)==null,"headset-only device not forced to earpiece");
        check(EndpointSelection.unique(Collections.<Endpoint>emptyList(),e->e.earpiece)==null,"empty endpoint list");
        check(EndpointSelection.unique((List<Endpoint>)null,e->e.earpiece)==null,"unobserved list");
        check(EndpointSelection.unique(Arrays.asList(earpiece,null),e->e.earpiece)==null,"invalid list not partially trusted");
        System.out.println("EndpointSelection: "+checks+" assertions passed");
    }
}
