package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerTargetMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

// Keep independently named faces of the same block as distinct graph selections
public final class GraphTargetSelection{
    public static final String ALIASES_TAG = "DataTargetAliases";
    public static final int MAX_ALIAS_LENGTH = 64;
    private GraphTargetSelection(){}

    // An explicitly resolved empty target clears a saved binding; ordinary scalar defaults do not
    public static CompoundTag resolveBinding(@Nullable CompoundTag saved, @Nullable String runtimeType,
                                             @Nullable CompoundTag runtimePayload){
        if("target".equals(runtimeType)) return runtimePayload == null ? new CompoundTag() : runtimePayload.copy();
        if("map".equals(runtimeType)) return ControllerTargetMap.first(runtimePayload);
        return saved == null ? new CompoundTag() : saved.copy();
    }

    // Let hosts resolve scalar names while retaining Target and target-map input support
    public static CompoundTag resolveNamedBinding(@Nullable String type, @Nullable CompoundTag payload,
            java.util.function.Function<String, CompoundTag> names){
        if(("string".equals(type) || "any".equals(type)) && payload != null
                && payload.contains("Value", net.minecraft.nbt.Tag.TAG_STRING)){
            CompoundTag target = names.apply(payload.getString("Value"));
            return target == null ? new CompoundTag() : target.copy();
        }
        return resolveBinding(null, type, payload);
    }

    // Unwired picker defaults may contain only an id; retain their complete saved selection
    public static CompoundTag resolveDefaultBinding(@Nullable CompoundTag saved, @Nullable String type,
                                                    @Nullable CompoundTag payload){
        if("target".equals(type) && (payload == null || !payload.contains("BlockPos"))
                && saved != null && !saved.isEmpty()) return saved.copy();
        return resolveBinding(saved, type, payload);
    }

    public static ControllerDiscoveryNode withFace(ControllerDiscoveryNode target, @Nullable Direction face){
        if(face == null) return target;
        String id = target.nodeId();
        int suffix = id.lastIndexOf("/face/");
        if(suffix >= 0 && Direction.byName(id.substring(suffix + 6)) != null) id = id.substring(0, suffix);
        return new ControllerDiscoveryNode(id + "/face/" + face.getSerializedName(), target.kind(),
                target.groupId(), target.blockId(), target.label(), target.subLevelId(), target.blockPos());
    }

    // Match saved selections by their current block and face, including legacy ids
    public static @Nullable String matchingFace(Collection<ControllerDiscoveryNode> selections,
            Map<String, Direction> faces, ControllerDiscoveryNode target, @Nullable Direction face){
        return selections.stream().filter(existing -> existing.hasSameBlockTarget(target)
                        && faces.get(existing.nodeId()) == face)
                .map(ControllerDiscoveryNode::nodeId).findFirst().orElse(null);
    }

    public static String normalizeAlias(String alias){
        String value = alias == null ? "" : alias.strip().replaceAll("[\\p{Cntrl}]", "");
        return value.length() <= MAX_ALIAS_LENGTH ? value : value.substring(0, MAX_ALIAS_LENGTH);
    }

    // Names affect section labels without changing saved target or port ids
    public static String label(ControllerDiscoveryNode target, @Nullable Direction face, String alias){
        String value = normalizeAlias(alias);
        if(!value.isBlank()) return value;
        String name = target.label().isBlank() ? target.nodeId() : target.label();
        return face == null ? name : name + " (" + face.getSerializedName() + ")";
    }
}
