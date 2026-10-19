package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphInputVisibilityTest{
    @Test
    void changingModesHidesInputsWithoutDiscardingTheirSavedValues(){
        CompoundTag data = new CompoundTag();
        GraphInputVisibility.configure(data, "", Map.of(
                "angle", new GraphInputVisibility.Condition("mode", List.of("servo"), "passthrough"),
                "speed", new GraphInputVisibility.Condition("mode", List.of("passthrough"), "passthrough")));
        Map<String, String> ports = Map.of("mode", "string", "angle", "number", "speed", "number");
        assertEquals(Map.of("mode", "string", "speed", "number"), GraphInputVisibility.filter(ports, data, new CompoundTag()));
        literal(data, "mode", "servo");
        literal(data, "angle", "30");
        CompoundTag saved = data.copy();
        assertEquals(Map.of("mode", "string", "angle", "number"), GraphInputVisibility.filter(ports, data, new CompoundTag()));
        assertEquals(saved, data);
        literal(data, "mode", "passthrough");
        assertFalse(GraphInputVisibility.filter(ports, data, new CompoundTag()).containsKey("angle"));
        assertEquals("30", data.getCompound("Defaults").getCompound("angle").getCompound("Payload").getString("Value"));
    }

    @Test
    void mergedPortsUseEachTargetsModeAndKeepUnconditionalTargetsVisible(){
        CompoundTag data = new CompoundTag();
        GraphInputVisibility.configure(data, "left", Map.of("angle",
                new GraphInputVisibility.Condition("mode", List.of("servo"), "passthrough")));
        GraphInputVisibility.configure(data, "right", Map.of("angle",
                new GraphInputVisibility.Condition("mode", List.of("servo"), "passthrough")));
        CompoundTag bindings = new CompoundTag();
        bindings.put("merged_angle", entries("left", "angle", "right", "angle"));
        bindings.put("left_mode", entries("left", "mode"));
        bindings.put("right_mode", entries("right", "mode"));
        Map<String, String> ports = Map.of("merged_angle", "number");
        assertTrue(GraphInputVisibility.filter(ports, data, bindings).isEmpty());
        literal(data, "right_mode", "servo");
        assertEquals(ports, GraphInputVisibility.filter(ports, data, bindings));
        literal(data, "right_mode", "passthrough");
        GraphInputVisibility.configure(data, "right", Map.of());
        assertEquals(ports, GraphInputVisibility.filter(ports, data, bindings));
    }

    @Test
    void writeBatchesSelectTheModeBeforeItsControlsAndSkipInactiveTargetWrites(){
        var writes = new java.util.ArrayList<String>();
        var provider = new com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider(){
            public Map<String, String> graphReadableData(){ return Map.of(); }
            public Map<String, String> graphWritableData(){ return Map.of("mode", "string", "angle", "number", "speed", "number"); }
            public Map<String, GraphInputVisibility.Condition> graphWritableInputConditions(){
                return Map.of("angle", new GraphInputVisibility.Condition("mode", List.of("servo"), "passthrough"),
                        "speed", new GraphInputVisibility.Condition("mode", List.of("passthrough"), "passthrough"));
            }
            public GraphValue readGraphValue(String port){ return GraphValue.string(""); }
            public boolean writeGraphValue(String port, GraphValue value){ writes.add(port); return true; }
        };
        Map<String, GraphValue> values = new java.util.LinkedHashMap<>();
        values.put("angle", GraphValue.number(30));
        values.put("speed", GraphValue.number(50));
        values.put("mode", GraphValue.string("servo"));
        assertTrue(provider.writeGraphValues(values));
        assertEquals(List.of("mode", "angle"), writes);
        writes.clear();
        assertTrue(provider.writeGraphValues(Map.of("angle", GraphValue.number(30))));
        assertTrue(writes.isEmpty());
    }

    private static void literal(CompoundTag data, String port, String value){
        CompoundTag payload = new CompoundTag();
        payload.putString("Value", value);
        CompoundTag literal = new CompoundTag();
        literal.put("Payload", payload);
        CompoundTag defaults = data.getCompound("Defaults");
        defaults.put(port, literal);
        data.put("Defaults", defaults);
    }

    private static ListTag entries(String... pairs){
        ListTag entries = new ListTag();
        for(int idx = 0; idx < pairs.length; idx += 2){
            CompoundTag entry = new CompoundTag();
            entry.putString("Target", pairs[idx]);
            entry.putString("Port", pairs[idx + 1]);
            entries.add(entry);
        }
        return entries;
    }
}
