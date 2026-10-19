package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Keep preferred browser categories first without hiding categories registered by a host
public final class GraphNodeBrowser{
    private GraphNodeBrowser(){}

    // The host supplies resolved ports so dynamically added execution inputs are included
    public static boolean standaloneExecutionSource(Map<String, String> inputs, Map<String, String> outputs){
        return !inputs.containsValue("exec") && outputs.containsValue("exec");
    }

    public static List<String> categories(Collection<String> preferred, Collection<String> available,
                                           Function<String, String> label){
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for(String category : preferred) if(available.contains(category)) ordered.add(category);
        List<String> remaining = new ArrayList<>(new LinkedHashSet<>(available));
        remaining.removeAll(ordered);
        remaining.sort(Comparator.comparing(label, String.CASE_INSENSITIVE_ORDER).thenComparing(Function.identity()));
        ordered.addAll(remaining);
        return List.copyOf(ordered);
    }
}
