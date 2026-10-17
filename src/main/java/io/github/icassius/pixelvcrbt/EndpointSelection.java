package io.github.icassius.pixelvcrbt;

import java.util.List;
import java.util.function.Predicate;

/** Return the exact system-supplied object only when its role is unique. */
final class EndpointSelection {
    static <T> T unique(List<T> choices,Predicate<T> matches){
        T selected=null;
        if(choices==null)return null;
        for(T choice:choices){if(choice==null)return null;if(matches.test(choice)){if(selected!=null)return null;selected=choice;}}
        return selected;
    }
}
