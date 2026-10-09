package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Keep reusable presentation defaults compatible with saved graph value wrappers
class GraphNodePresentationTest{
    @Test
    void sectionsOrderPortsAndPreserveNestedDefaults(){
        String type = "test:presentation";
        GraphNodePresentationRegistry.register(type, new GraphNodePresentationRegistry.Presentation(
                Map.of("paths", GraphValue.list(List.of(Map.of("x", 42))), "mode", GraphValue.string("hold")),
                List.of(new GraphNodePresentationRegistry.Section("route", "Route", List.of("paths", "mode"))),
                Map.of("mode", List.of("hold", "release"))));
        CompoundTag data = new CompoundTag();
        GraphNodePresentationRegistry.initialize(type, data);
        var path = data.getCompound("Defaults").getCompound("paths").getCompound("Payload").getCompound("0");
        assertEquals("map", path.getString("Type"));
        assertEquals(42, path.getCompound("Payload").getCompound("x").getCompound("Payload").getDouble("Value"));
        assertEquals("paths", data.getList("InputOrder", 8).getString(0));
        assertEquals("hold", data.getCompound("InputOptions").getList("mode", 8).getString(0));
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("mode", "string");
        ports.put("other", "number");
        ports.put("paths", "list");
        assertEquals(List.of("paths", "mode", "other"), List.copyOf(GraphNodePresentationRegistry.orderedInputs(type, ports).keySet()));
        assertThrows(IllegalStateException.class, () -> GraphNodePresentationRegistry.register(type,
                new GraphNodePresentationRegistry.Presentation(Map.of(), List.of())));
    }

    @Test
    void typedMembersHandleRawAndWrappedCollections(){
        var raw = GraphValue.map(Map.of("nested", List.of(Map.of("amount", 7))));
        assertEquals(7, raw.member("nested").entries().getFirst().member("amount").asNumber());
        assertEquals(0, raw.member("missing").asNumber());
        assertThrows(UnsupportedOperationException.class, () -> raw.member("nested").entries().clear());
    }
}
