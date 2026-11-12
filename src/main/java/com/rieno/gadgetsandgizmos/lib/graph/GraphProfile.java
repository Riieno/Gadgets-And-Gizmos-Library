package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Map;

// Publish immutable execution timings without retaining the running graph
public record GraphProfile(long ticks, long totalNanos, long lastNanos, long maxNanos,
        Map<String, Node> nodes){
    public static final GraphProfile EMPTY = new GraphProfile(0, 0, 0, 0, Map.of());

    public GraphProfile{ nodes = Map.copyOf(nodes); }

    public double averageNanos(){ return ticks == 0 ? 0 : totalNanos / (double)ticks; }

    // Self time excludes nested nodes; inclusive time includes their work
    public record Node(long calls, long selfNanos, long inclusiveNanos, long maxSelfNanos){
        public double averageNanos(){ return calls == 0 ? 0 : selfNanos / (double)calls; }

        // Compare inclusive and self timings on the same per-call basis
        public double averageInclusiveNanos(){ return calls == 0 ? 0 : inclusiveNanos / (double)calls; }

        public Node plus(Node val){
            return new Node(calls + val.calls, selfNanos + val.selfNanos,
                    inclusiveNanos + val.inclusiveNanos, Math.max(maxSelfNanos, val.maxSelfNanos));
        }
    }
}
