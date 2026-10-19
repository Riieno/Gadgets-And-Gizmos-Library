package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphWebCatalogTest{
    @Test void trackerTelemetryRetainsItsDeclaredPortGroups(){
        var ports = com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi.ports();
        var order = List.copyOf(ports.keySet());
        assertEquals(List.of("position", "eye_position", "velocity", "look_hit", "look_block_position",
                "look_sub_level_position", "relative_angle"), order.subList(0, 7));
        assertEquals(List.of("look_block", "look_face", "look_sub_level", "main_hand", "off_hand"), order.subList(order.size() - 5, order.size()));
        assertThrows(UnsupportedOperationException.class, ports::clear);
    }

    @Test void exportRetainsPortOrderDynamicDefaultsAndNumericPrecision(){
        var schema = new GraphNodeDefinition("test:node", "math", GraphPortOrder.ports("Y", "number", "X", "number"),
                GraphPortOrder.ports("result", "number"), false);
        CompoundTag defaults = new CompoundTag();defaults.putDouble("Value", 0.12345678901234567);
        var node = GraphWebCatalog.node(schema, "Node", "Math", 0xFF123456, defaults,
                GraphPortOrder.ports("Y", "number", "X", "number", "extra", "number"), schema.outputs());
        assertEquals(List.of("Y", "X", "extra"), node.getAsJsonObject("inputs").keySet().stream().toList());
        assertEquals("#123456", node.get("color").getAsString());
        assertEquals(0.12345678901234567, node.getAsJsonObject("defaultData").get("Value").getAsDouble());
        assertEquals(List.of("Y", "X", "extra"), node.getAsJsonObject("defaultData").getAsJsonArray("InputOrder")
                .asList().stream().map(value -> value.getAsString()).toList());
        assertFalse(defaults.contains("InputOrder"));
        assertFalse(node.getAsJsonObject("baseInputs").has("extra"));
    }
}
