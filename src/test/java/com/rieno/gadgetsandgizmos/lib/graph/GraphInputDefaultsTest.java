package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Preserve authored zero, false and empty values when sharing configuration reads with static analysis
class GraphInputDefaultsTest{
    @Test void authoredWhitespaceAndEmptyTextAreNotMissingValues(){
        var data = new CompoundTag();
        data.putString("Text", "legacy");
        for(String text : java.util.List.of(" ", "  ", "", " padded ")){
            var payload = new CompoundTag(); payload.putString("Value", text);
            var encoded = new CompoundTag(); encoded.putString("Type", "string"); encoded.put("Payload", payload);
            var defaults = new CompoundTag(); defaults.put("text", encoded); data.put("Defaults", defaults);
            assertEquals(text, GraphInputDefaults.text(data, "text", "fallback"));
            assertEquals(text, GraphValueText.parse("string", GraphInputDefaults.text(data, "text", "fallback")).asString());
        }
        data.remove("Defaults");
        assertEquals("legacy", GraphInputDefaults.text(data, "text", "fallback"));
        assertEquals("fallback", GraphInputDefaults.text(data, "missing", "fallback"));
    }

    @Test void authoredDefaultsTakePriorityOverLegacyProperties(){
        CompoundTag data = new CompoundTag();
        CompoundTag defaults = new CompoundTag();
        CompoundTag encoded = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        encoded.putString("Type", "number");
        payload.putDouble("Value", 0);
        encoded.put("Payload", payload);
        defaults.put("value", encoded);
        data.put("Defaults", defaults);
        data.putDouble("Value", 9);
        assertEquals(encoded, GraphInputDefaults.configuredValue(data, "value"));
        GraphInputDefaults.configuredValue(data, "value").getCompound("Payload").putDouble("Value", 5);
        assertEquals(0, encoded.getCompound("Payload").getDouble("Value"));
    }

    @Test void absentConfigurationStaysAbsent(){
        CompoundTag data = new CompoundTag();
        assertTrue(GraphInputDefaults.configuredValue(data, "value").isEmpty());
        assertTrue(GraphInputDefaults.configuredValue(data, null).isEmpty());
        assertTrue(data.isEmpty());
    }

    @Test void legacyPrimitivePropertiesKeepTheirTypesAndValues(){
        CompoundTag data = new CompoundTag();
        data.putDouble("Number", 0);
        data.putBoolean("Enabled", false);
        data.putString("Text", "");
        assertEquals("number", GraphInputDefaults.configuredValue(data, "number").getString("Type"));
        assertEquals(0, GraphInputDefaults.configuredValue(data, "number").getCompound("Payload").getDouble("Value"));
        assertEquals("boolean", GraphInputDefaults.configuredValue(data, "enabled").getString("Type"));
        assertFalse(GraphInputDefaults.configuredValue(data, "enabled").getCompound("Payload").getBoolean("Value"));
        assertEquals("string", GraphInputDefaults.configuredValue(data, "text").getString("Type"));
        assertEquals("", GraphInputDefaults.configuredValue(data, "text").getCompound("Payload").getString("Value"));
    }
}
