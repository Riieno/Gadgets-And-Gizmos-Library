package com.rieno.gadgetsandgizmos.lib.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

// Group controls connected by shared domain keys in stable traversal order
public final class ControlDomains{
    private ControlDomains(){}

    public static <T, K> List<List<T>> connected(List<T> values, Function<T, ? extends Collection<K>> domains){
        Map<K, List<Integer>> members = new HashMap<>();
        List<Collection<K>> keys = new ArrayList<>(values.size());
        for(int idx = 0; idx < values.size(); idx++){
            Collection<K> domain = List.copyOf(domains.apply(values.get(idx)));
            keys.add(domain);
            for(K key : domain) members.computeIfAbsent(key, ignored -> new ArrayList<>()).add(idx);
        }
        boolean[] visited = new boolean[values.size()];
        List<List<T>> res = new ArrayList<>();
        for(int idx = 0; idx < values.size(); idx++){
            if(visited[idx]) continue;
            visited[idx] = true;
            List<T> group = new ArrayList<>();
            ArrayDeque<Integer> frontier = new ArrayDeque<>();
            frontier.add(idx);
            while(!frontier.isEmpty()){
                int current = frontier.removeFirst();
                group.add(values.get(current));
                TreeSet<Integer> candidates = new TreeSet<>();
                for(K key : keys.get(current)){
                    List<Integer> connected = members.remove(key);
                    if(connected != null) candidates.addAll(connected);
                }
                for(int candidate : candidates){
                    if(visited[candidate]) continue;
                    visited[candidate] = true;
                    frontier.addLast(candidate);
                }
            }
            res.add(List.copyOf(group));
        }
        return List.copyOf(res);
    }
}
