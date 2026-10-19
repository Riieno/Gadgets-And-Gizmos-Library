package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Index section ownership and header positions once for one structural graph layout
public final class GraphPortIndex<S>{
    private final Map<String, S> sections;
    private final Map<String, S> starts;

    private GraphPortIndex(Map<String, S> sections, Map<String, S> starts){
        this.sections = Collections.unmodifiableMap(sections);
        this.starts = Collections.unmodifiableMap(starts);
    }

    public static <S> GraphPortIndex<S> of(Collection<String> order, Collection<S> sections,
                                          Function<S, ? extends Collection<String>> members){
        Map<String, S> owners = new LinkedHashMap<>();
        if(sections != null){
            for(S section : sections){
                for(String port : members.apply(section)) owners.putIfAbsent(port, section);
            }
        }
        Map<String, S> starts = new LinkedHashMap<>();
        Set<S> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if(order != null){
            for(String port : order){
                S section = owners.get(port);
                if(section != null && seen.add(section)) starts.put(port, section);
            }
        }
        return new GraphPortIndex<>(owners, starts);
    }

    public S section(String port){ return sections.get(port); }
    public S startingAt(String port){ return starts.get(port); }
}
