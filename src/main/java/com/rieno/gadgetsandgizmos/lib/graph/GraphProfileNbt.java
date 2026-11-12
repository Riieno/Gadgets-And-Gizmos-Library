package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;

// Transport profiler snapshots independently of graph documents and runtime state
public final class GraphProfileNbt{
    private GraphProfileNbt(){}

    public static CompoundTag write(GraphProfile profile){
        var tag = new CompoundTag();
        tag.putLong("Ticks", profile.ticks());
        tag.putLong("Total", profile.totalNanos());
        tag.putLong("Last", profile.lastNanos());
        tag.putLong("Max", profile.maxNanos());
        var nodes = new ListTag();
        profile.nodes().forEach((id, val) -> {
            var node = new CompoundTag();
            node.putString("Id", id);
            node.putLong("Calls", val.calls());
            node.putLong("Self", val.selfNanos());
            node.putLong("Inclusive", val.inclusiveNanos());
            node.putLong("Max", val.maxSelfNanos());
            nodes.add(node);
        });
        tag.put("Nodes", nodes);
        return tag;
    }

    public static GraphProfile read(CompoundTag tag){
        var nodes = new LinkedHashMap<String, GraphProfile.Node>();
        for(var raw : tag.getList("Nodes", Tag.TAG_COMPOUND)){
            var node = (CompoundTag)raw;
            nodes.put(node.getString("Id"), new GraphProfile.Node(node.getLong("Calls"),
                    node.getLong("Self"), node.getLong("Inclusive"), node.getLong("Max")));
        }
        return new GraphProfile(tag.getLong("Ticks"), tag.getLong("Total"),
                tag.getLong("Last"), tag.getLong("Max"), nodes);
    }
}
