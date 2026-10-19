package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

// Search authored nodes in their supplied order and wrap navigation in either direction
public final class GraphCanvasSearch{
    private GraphCanvasSearch(){}

    public static <N extends GraphModel.Node> List<N> matches(Collection<N> nodes, String query,
                                                             Function<N, String> label){
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if(text.isEmpty()) return List.of();
        return nodes.stream().filter(node -> (node.type() + " " + label.apply(node))
                .toLowerCase(Locale.ROOT).contains(text)).toList();
    }

    public static <N extends GraphModel.Node> N next(List<N> matches, String current, boolean previous){
        if(matches.isEmpty()) return null;
        int index = -1;
        for(int idx = 0; idx < matches.size(); idx++) if(matches.get(idx).id().equals(current)){ index = idx; break; }
        if(index < 0) return previous ? matches.getLast() : matches.getFirst();
        return matches.get(Math.floorMod(index + (previous ? -1 : 1), matches.size()));
    }
}
