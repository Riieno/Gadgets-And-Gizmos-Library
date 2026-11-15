package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Edit script port schemas while preserving defaults and player-selected order
public final class ScriptNodePorts{
    public static final List<String> TYPES = List.of("number", "boolean", "string", "list", "map", "any", "direction", "color", "target", "frequency", "player");
    private ScriptNodePorts(){}

    // Let scripts determine their data types while retaining the typed API for other hosts
    public static String add(CompoundTag data, boolean output, Set<String> existing){
        return add(data, output, "any", existing);
    }

    // Read legacy typed schemas as generic ports without rewriting their saved names or order
    public static CompoundTag inferred(CompoundTag ports){
        CompoundTag res = new CompoundTag();
        ports.getAllKeys().forEach(port -> res.putString(port, "any"));
        return res;
    }

    public static String add(CompoundTag data, boolean output, String type, Set<String> existing){
        if(!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported script port type");
        if(existing.size() >= 64) throw new IllegalArgumentException("Scripts support at most 64 ports per side");
        Set<String> used = new LinkedHashSet<>(existing);
        String prefix = output ? "output" : "input";
        String port = prefix;
        int idx = 2;
        while(used.contains(port)) port = prefix + "_" + idx++;
        String key = output ? "DynamicOutputs" : "DynamicInputs";
        CompoundTag ports = data.getCompound(key);
        ports.putString(port, type);
        data.put(key, ports);
        List<String> order = new ArrayList<>(existing);
        order.add(port);
        GraphPortOrder.write(data, output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER, order);
        return port;
    }

    public static boolean rename(CompoundTag data, String port, String requested, boolean output, Set<String> existing){
        String name = requested == null ? "" : requested.strip();
        String key = output ? "DynamicOutputs" : "DynamicInputs";
        CompoundTag ports = data.getCompound(key);
        if(!ports.contains(port) || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)
                || name.equals(port) || existing.contains(name)) return false;
        String type = ports.getString(port);
        ports.remove(port);
        ports.putString(name, type);
        data.put(key, ports);
        for(String settings : List.of("Defaults", output ? "OutputLabels" : "InputLabels")){
            CompoundTag vals = data.getCompound(settings);
            if(vals.contains(port)){ vals.put(name, vals.get(port).copy()); vals.remove(port); data.put(settings, vals); }
        }
        GraphPortOrder.write(data, output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER,
                existing.stream().map(val -> val.equals(port) ? name : val).toList());
        return true;
    }
}
