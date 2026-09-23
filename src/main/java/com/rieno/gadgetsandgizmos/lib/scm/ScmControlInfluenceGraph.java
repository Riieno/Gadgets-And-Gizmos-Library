package com.rieno.gadgetsandgizmos.lib.scm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// Discover downstream actuators without traversing another independent control
public final class ScmControlInfluenceGraph{
    private ScmControlInfluenceGraph(){}

    public static final class Search<T>{
        private final ArrayDeque<T> pending = new ArrayDeque<>();
        private final Set<T> visited = new HashSet<>();
        private final List<T> outputs = new ArrayList<>();
        private final Function<T, List<T>> neighbours;
        private final Predicate<T> actuator;
        private final Predicate<T> boundary;
        private final int maximumNodes;
        private boolean truncated;

        public Search(T start, Function<T, List<T>> neighbours, Predicate<T> actuator,
                      Predicate<T> boundary, int maximumNodes){
            this.neighbours = neighbours;
            this.actuator = actuator;
            this.boundary = boundary;
            this.maximumNodes = Math.max(1, maximumNodes);
            if(start != null){
                pending.add(start);
                visited.add(start);
            }
        }

        // Retain unfinished traversal for the next game-thread slice
        public void advance(int maximumWork, long maximumNanos){
            if(maximumNanos <= 0L) return;
            long started = System.nanoTime();
            for(int idx = 0; idx < Math.max(0, maximumWork) && !pending.isEmpty(); idx++){
                if(System.nanoTime() - started >= maximumNanos) break;
                T node = pending.removeFirst();
                if(actuator.test(node)) outputs.add(node);
                for(T next : neighbours.apply(node)){
                    if(next == null || visited.contains(next) || boundary.test(next)) continue;
                    if(visited.size() >= maximumNodes){
                        truncated = true;
                        continue;
                    }
                    visited.add(next);
                    pending.addLast(next);
                }
            }
        }

        public boolean complete(){ return pending.isEmpty() && !truncated; }
        public boolean finished(){ return pending.isEmpty(); }
        public List<T> outputs(){ return List.copyOf(outputs); }
    }
}
