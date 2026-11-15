package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

// Retain a reusable script independently of its placed instances and execution state
public final class ScriptFunction{
    public static final String REFERENCE = "LuaFunctionId";
    public static final String APPLIED_VERSION = "LuaAppliedVersion";
    public static final String DISPLAY_NAME = "LuaFunctionName";
    private static final List<String> SHARED = java.util.stream.Stream.concat(java.util.stream.Stream.of(LuaGraphNode.SOURCE, DISPLAY_NAME), ScriptPorts.KEYS.stream()).toList();
    private final String id;
    private String name;
    private final CompoundTag data;

    public ScriptFunction(String id, String name, CompoundTag data){
        this.id = UUID.fromString(id).toString();
        this.name = name == null || name.isBlank() ? "Lua Function" : name.strip();
        this.data = data.copy();
    }

    public String id(){ return id; }
    // Keep legacy references stable across detached document reads before their first save
    public static String legacyId(String scope, String nodeId){
        return UUID.nameUUIDFromBytes((scope + "\u0000" + nodeId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    public String name(){ return name; }
    public void name(String val){ if(val != null && !val.isBlank()) name = val.strip().substring(0, Math.min(64, val.strip().length())); }
    public CompoundTag data(){ return data; }

    // Rename the definition and its authored Name input while retaining its UUID and source
    public boolean rename(String val){
        if(val == null || val.isBlank() || val.strip().length() > 64 || val.chars().anyMatch(Character::isISOControl)) return false;
        String next = val.strip();
        if(name.equals(next) && next.equals(data.getString(DISPLAY_NAME))) return false;
        name = next; data.putString(DISPLAY_NAME, next);
        var encoded = new CompoundTag(); var payload = new CompoundTag();
        encoded.putString("Type", "string"); payload.putString("Value", next); encoded.put("Payload", payload);
        var defaults = data.getCompound("Defaults"); defaults.put("name", encoded); data.put("Defaults", defaults); return true;
    }

    // Remove every instance and attached wire through host-supplied graph accessors
    public static <N, E> java.util.Set<String> removeInstances(List<N> nodes, List<E> edges, String id,
            java.util.function.Function<N, String> reference, java.util.function.Function<N, String> nodeId,
            java.util.function.Function<E, String> from, java.util.function.Function<E, String> to){
        var removed = new java.util.HashSet<String>();
        nodes.removeIf(node -> { if(!id.equals(reference.apply(node))) return false; removed.add(nodeId.apply(node)); return true; });
        edges.removeIf(edge -> removed.contains(from.apply(edge)) || removed.contains(to.apply(edge)));
        return java.util.Set.copyOf(removed);
    }

    // Update shared code and schemas while retaining each instance's configured input values
    public void apply(CompoundTag instance){
        int version = fingerprint(data);
        if(id.equals(instance.getString(REFERENCE)) && instance.contains(APPLIED_VERSION)
                && instance.getInt(APPLIED_VERSION) == version && fingerprint(instance) == version) return;
        instance.putString(REFERENCE, id);
        for(String key : SHARED){
            if(data.contains(key)) instance.put(key, data.get(key).copy());
            else instance.remove(key);
        }
        CompoundTag defaults = instance.getCompound("Defaults");
        if(data.contains(DISPLAY_NAME)) defaults.put("name", data.getCompound("Defaults").getCompound("name").copy());
        data.getCompound("Defaults").getAllKeys().forEach(port -> {
            if(!defaults.contains(port)) defaults.put(port, data.getCompound("Defaults").get(port).copy());
        });
        instance.put("Defaults", defaults);
        instance.putInt(APPLIED_VERSION, fingerprint(instance));
    }

    public static int fingerprint(CompoundTag val){
        int hash = 1;
        for(String key : SHARED) hash = 31 * hash + java.util.Objects.hashCode(val.get(key));
        return hash;
    }

    public void update(CompoundTag instance){
        for(String key : SHARED){
            if(DISPLAY_NAME.equals(key)) continue;
            if(instance.contains(key)) data.put(key, instance.get(key).copy());
            else data.remove(key);
        }
        var defaults = data.getCompound("Defaults");
        instance.getCompound("Defaults").getAllKeys().forEach(port -> {
            if(!defaults.contains(port)) defaults.put(port, instance.getCompound("Defaults").get(port).copy());
        });
        data.put("Defaults", defaults);
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putString("Name", name); tag.put("Data", data.copy());
        return tag;
    }

    public static ScriptFunction fromTag(CompoundTag tag){ return new ScriptFunction(tag.getString("Id"), tag.getString("Name"), tag.getCompound("Data")); }
}
