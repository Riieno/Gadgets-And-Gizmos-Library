package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.Collection;

/** Keeps connected dynamic ports available when a live schema sample is incomplete. */
public final class GraphDynamicPortSchema {
    private GraphDynamicPortSchema() {
    }

    public static CompoundTag retainConnectedOutputs(CompoundTag resolved, CompoundTag previous,
                                                     Collection<String> connectedPorts) {
        CompoundTag result = resolved == null ? new CompoundTag() : resolved.copy();
        if (previous == null || connectedPorts == null) return result;
        for (String port : connectedPorts) {
            if (port != null && previous.contains(port) && !result.contains(port)) {
                result.putString(port, previous.getString(port));
            }
        }
        return result;
    }
}
