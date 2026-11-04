package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// Share saved target references and live host target lists across graph implementations
public final class TargetGraphNodes{
    public static final String TARGET_DATA = "TargetData";

    private TargetGraphNodes(){}

    // Let the consuming mod own the node ID and its single Target output
    public static GraphNodeDefinition targetDefinition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "target").toString(),
                "data", Map.of(), GraphPortOrder.ports("target", "target"), false);
    }

    // Expose a passive list which existing collection nodes can iterate
    public static GraphNodeDefinition getTargetsDefinition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "get_targets").toString(),
                "data", Map.of(), GraphPortOrder.ports("targets", "list"), false);
    }

    // Persist one modal selection independently of runtime inputs and other nodes
    public static void select(CompoundTag data, @Nullable ControllerDiscoveryNode target){
        if(target == null){
            data.remove("Target");
            data.remove("TargetLabel");
            data.remove(TARGET_DATA);
            return;
        }
        data.putString("Target", target.nodeId());
        data.putString("TargetLabel", target.label().isBlank() ? target.nodeId() : target.label());
        data.put(TARGET_DATA, target.toTag());
    }

    // Preserve packed positions, sublevel UUIDs and target metadata without mutable NBT ownership
    public static GraphValue reference(CompoundTag data){
        return new GraphValue("target", GraphDataNbt.fromCompound(data));
    }

    // Return a fresh payload for the host's native target value
    public static CompoundTag payload(GraphValue reference){
        return reference != null && "target".equals(reference.type())
                && reference.value() instanceof GraphDataValue.CompoundValue data
                ? GraphDataNbt.toCompound(data) : new CompoundTag();
    }

    // Read the saved selection without requiring an execution pulse or a loaded target
    public static Map<String, GraphValue> target(GraphExecutionContext ctx, Map<String, GraphValue> inputs){
        GraphValue selected = ctx.configuration(TARGET_DATA);
        return Map.of("target", selected != null && "target".equals(selected.type())
                && selected.value() instanceof GraphDataValue.CompoundValue
                ? selected : reference(new CompoundTag()));
    }

    // Sample the host on each evaluation and retain typed target entries in host order
    public static Map<String, GraphValue> getTargets(GraphExecutionContext ctx, Map<String, GraphValue> inputs){
        GraphTargetSource source = ctx.service(GraphHostServices.TARGETS).orElse(List::of);
        List<GraphValue> targets = source.targets();
        return Map.of("targets", GraphValue.list(targets == null ? List.of() : targets.stream()
                .filter(Objects::nonNull).filter(val -> "target".equals(val.type())).toList()));
    }
}
