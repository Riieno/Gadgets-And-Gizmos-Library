package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

// Ordered typed target maps can flow into a multi-target consumer or resolve to their first binding
public final class ControllerTargetMap{
    private ControllerTargetMap(){}

    public static CompoundTag encode(Collection<ControllerDiscoveryNode> targets){
        CompoundTag result = new CompoundTag();
        int index = 0;
        for(ControllerDiscoveryNode target : targets == null ? List.<ControllerDiscoveryNode>of() : targets){
            if(target == null || target.blockPos() == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Type", "target");entry.put("Payload", target.toTag());
            result.put(String.format(Locale.ROOT, "target_%08d", index++), entry);
        }
        return result;
    }

    public static List<ControllerDiscoveryNode> read(CompoundTag payload){
        if(payload == null || payload.isEmpty()) return List.of();
        ControllerDiscoveryNode single = ControllerDiscoveryNode.fromTag(payload);
        if(single != null && single.blockPos() != null) return List.of(single);
        LinkedHashMap<String, ControllerDiscoveryNode> targets = new LinkedHashMap<>();
        for(String key : payload.getAllKeys().stream().sorted().toList()){
            if(!payload.contains(key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = payload.getCompound(key);
            if(!"target".equals(entry.getString("Type"))) continue;
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(entry.getCompound("Payload"));
            if(target != null && target.blockPos() != null) targets.putIfAbsent(target.nodeId(), target);
        }
        return List.copyOf(targets.values());
    }

    public static CompoundTag first(CompoundTag payload){
        List<ControllerDiscoveryNode> targets = read(payload);
        return targets.isEmpty() ? new CompoundTag() : targets.getFirst().toTag();
    }
}
