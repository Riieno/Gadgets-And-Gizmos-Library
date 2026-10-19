package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Select inputs using the configured value of another input without discarding saved values
public final class GraphInputVisibility{
    public static final String CONDITIONS_TAG = "TargetInputConditions";

    private GraphInputVisibility(){}

    public record Condition(String selector, List<String> values, String fallback){
        public Condition{
            values = List.copyOf(values);
        }
    }

    // Retain a provider's conditions under its stable target id
    public static void configure(CompoundTag data, String target, Map<String, Condition> conditions){
        CompoundTag targets = data.getCompound(CONDITIONS_TAG);
        CompoundTag rules = new CompoundTag();
        conditions.forEach((port, condition) -> {
            CompoundTag rule = new CompoundTag();
            rule.putString("Selector", condition.selector());
            rule.putString("Fallback", condition.fallback());
            ListTag values = new ListTag();
            condition.values().forEach(value -> values.add(StringTag.valueOf(value)));
            rule.put("Values", values);
            rules.put(port, rule);
        });
        if(rules.isEmpty()) targets.remove(target);
        else targets.put(target, rules);
        if(targets.isEmpty()) data.remove(CONDITIONS_TAG);
        else data.put(CONDITIONS_TAG, targets);
    }

    // A merged input remains visible when at least one bound target accepts it
    public static Map<String, String> filter(Map<String, String> ports, CompoundTag data, CompoundTag bindings){
        CompoundTag targets = data.getCompound(CONDITIONS_TAG);
        if(targets.isEmpty()) return ports;
        Map<String, Map<String, String>> targetPorts = new LinkedHashMap<>();
        for(String port : bindings.getAllKeys()){
            for(Tag raw : bindings.getList(port, Tag.TAG_COMPOUND)){
                CompoundTag binding = (CompoundTag) raw;
                targetPorts.computeIfAbsent(binding.getString("Target"), ignored -> new LinkedHashMap<>())
                        .put(binding.getString("Port"), port);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        ports.forEach((port, type) -> {
            ListTag entries = bindings.getList(port, Tag.TAG_COMPOUND);
            boolean visible = entries.isEmpty() && accepts(data, targets.getCompound(""), port, "");
            for(Tag raw : entries){
                CompoundTag binding = (CompoundTag) raw;
                String target = binding.getString("Target");
                CompoundTag rules = targets.getCompound(target);
                String field = binding.getString("Port");
                String selector = rules.getCompound(field).getString("Selector");
                String selectorPort = targetPorts.getOrDefault(target, Map.of()).getOrDefault(selector, selector);
                visible |= accepts(data, rules, field, selectorPort);
            }
            if(visible) result.put(port, type);
        });
        return result;
    }

    private static boolean accepts(CompoundTag data, CompoundTag rules, String port, String selectorPort){
        if(!rules.contains(port)) return true;
        CompoundTag rule = rules.getCompound(port);
        String selector = selectorPort.isBlank() ? rule.getString("Selector") : selectorPort;
        CompoundTag value = data.getCompound("Defaults").getCompound(selector).getCompound("Payload");
        String selected = value.contains("Value", Tag.TAG_STRING) ? value.getString("Value") : rule.getString("Fallback");
        return rule.getList("Values", Tag.TAG_STRING).stream().anyMatch(entry -> entry.getAsString().equalsIgnoreCase(selected));
    }

    // Resolve a write batch against its requested selector values before mutating a target
    public static Map<String, GraphValue> filterValues(Map<String, GraphValue> values, Map<String, Condition> conditions){
        Map<String, GraphValue> result = new LinkedHashMap<>();
        values.forEach((port, value) -> {
            Condition condition = conditions.get(port);
            GraphValue selector = condition == null ? null : values.get(condition.selector());
            String selected = condition == null ? "" : selector == null ? condition.fallback() : selector.asString();
            if(condition == null || condition.values().stream().anyMatch(entry -> entry.equalsIgnoreCase(selected))){
                result.put(port, value);
            }
        });
        return result;
    }
}
