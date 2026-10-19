package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Retain declared and sampled port order across immutable schemas and saved node data
public final class GraphPortOrder{
    public static final String INPUT_ORDER = "InputOrder";
    public static final String OUTPUT_ORDER = "OutputOrder";
    public static final String ENTRY_ORDER = "EntryOrder";

    private GraphPortOrder(){}

    public static <K, V> Map<K, V> copy(Map<K, V> values){
        Map<K, V> copy = new LinkedHashMap<>();
        values.forEach((key, val) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(val)));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, String> ports(String... pairs){
        if(pairs.length % 2 != 0) throw new IllegalArgumentException("Ports require name/type pairs");
        Map<String, String> ports = new LinkedHashMap<>();
        for(int idx = 0; idx < pairs.length; idx += 2){
            if(ports.putIfAbsent(Objects.requireNonNull(pairs[idx]), Objects.requireNonNull(pairs[idx + 1])) != null){
                throw new IllegalArgumentException("Duplicate port: " + pairs[idx]);
            }
        }
        return copy(ports);
    }

    @SafeVarargs
    public static <K, V> Map<K, V> entries(Map.Entry<K, V>... entries){
        Map<K, V> result = new LinkedHashMap<>();
        for(var entry : entries){
            if(result.putIfAbsent(entry.getKey(), entry.getValue()) != null){
                throw new IllegalArgumentException("Duplicate entry: " + entry.getKey());
            }
        }
        return copy(result);
    }

    public static List<String> read(CompoundTag data, String key){
        return data.getList(key, Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }

    public static void write(CompoundTag data, String key, Collection<String> ports){
        ListTag order = new ListTag();
        new LinkedHashSet<>(ports).forEach(port -> order.add(StringTag.valueOf(port)));
        data.put(key, order);
    }

    public static List<String> keys(Collection<String> available, Collection<String> preferred){
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for(String key : preferred) if(available.contains(key)) result.add(key);
        result.addAll(numberedKeys(available));
        return List.copyOf(result);
    }

    public static List<String> withChildren(Collection<String> order, String parent, Collection<String> children){
        List<String> result = new ArrayList<>();
        for(String port : order){
            if(children.contains(port)) continue;
            result.add(port);
            if(port.equals(parent)) result.addAll(children);
        }
        for(String port : children) if(!result.contains(port)) result.add(port);
        return List.copyOf(result);
    }

    // Keep each section contiguous in its declared order, starting at its first existing port
    public static List<String> grouped(Collection<String> order, Collection<? extends Collection<String>> groups){
        LinkedHashSet<String> available = new LinkedHashSet<>();
        if(order != null) for(String port : order) if(port != null && !port.isBlank()) available.add(port);
        Map<String, Integer> owners = new LinkedHashMap<>();
        List<List<String>> sections = new ArrayList<>();
        if(groups != null){
            for(Collection<String> group : groups){
                if(group == null) continue;
                List<String> members = new ArrayList<>();
                for(String port : group){
                    if(available.contains(port) && owners.putIfAbsent(port, sections.size()) == null) members.add(port);
                }
                sections.add(members);
            }
        }
        List<String> result = new ArrayList<>();
        LinkedHashSet<Integer> emitted = new LinkedHashSet<>();
        for(String port : available){
            Integer owner = owners.get(port);
            if(owner == null) result.add(port);
            else if(emitted.add(owner)) result.addAll(sections.get(owner));
        }
        return List.copyOf(result);
    }

    // Include expanded child ports immediately after their owning parent, including nested maps
    public static List<String> withDescendants(Collection<String> order, Collection<String> roots,
                                                Map<String, String> parents){
        Map<String, List<String>> children = new LinkedHashMap<>();
        if(order != null && parents != null && !parents.isEmpty()){
            for(String port : order){
                if(port == null || port.isBlank()) continue;
                String parent = parents.get(port);
                if(parent == null || parent.isBlank() || parent.equals(port)) continue;
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(port);
            }
        }
        List<String> seeds = new ArrayList<>();
        if(roots != null) for(String port : roots) if(port != null && !port.isBlank()) seeds.add(port);
        ArrayDeque<String> pending = new ArrayDeque<>();
        for(int idx = seeds.size() - 1; idx >= 0; idx--) pending.push(seeds.get(idx));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        while(!pending.isEmpty()){
            String port = pending.pop();
            if(!result.add(port)) continue;
            List<String> branch = children.getOrDefault(port, List.of());
            for(int idx = branch.size() - 1; idx >= 0; idx--) pending.push(branch.get(idx));
        }
        return List.copyOf(result);
    }

    public static <V> Map<String, V> ordered(Map<String, V> ports, Collection<String> order){
        Map<String, V> result = new LinkedHashMap<>();
        for(String port : order) if(ports.containsKey(port)) result.put(port, ports.get(port));
        ports.forEach(result::putIfAbsent);
        return copy(result);
    }

    public static Map<String, String> merge(Map<String, String> declared, CompoundTag dynamic,
                                             Collection<String> order){
        Map<String, String> ports = new LinkedHashMap<>(declared);
        for(String key : numberedKeys(dynamic.getAllKeys())) ports.put(key, dynamic.getString(key));
        return ordered(ports, order);
    }

    // Recover numeric list indices and generated ordinals in older saves without sorting field names
    public static List<String> numberedKeys(Collection<String> keys){
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for(String key : keys){
            int start = ordinalStart(key);
            if(start < key.length()) groups.computeIfAbsent(key.substring(0, start), ignored -> new ArrayList<>()).add(key);
        }
        groups.values().forEach(group -> group.sort((a, b) ->
                new BigInteger(a.substring(ordinalStart(a))).compareTo(new BigInteger(b.substring(ordinalStart(b))))));
        Map<String, Integer> indices = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        for(String key : keys){
            int start = ordinalStart(key);
            if(start == key.length()) result.add(key);
            else{
                String prefix = key.substring(0, start);
                int idx = indices.getOrDefault(prefix, 0);
                result.add(groups.get(prefix).get(idx));
                indices.put(prefix, idx + 1);
            }
        }
        return List.copyOf(result);
    }

    private static int ordinalStart(String key){
        int start = key.length();
        while(start > 0 && key.charAt(start - 1) >= '0' && key.charAt(start - 1) <= '9') start--;
        return start;
    }
}
