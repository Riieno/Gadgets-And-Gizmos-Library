package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTextInputsTest {
    @Test
    void compiledJoinRetainsItsSchemaUntilRecompiled(){
        CompoundTag data = new CompoundTag();
        GraphTextInputs.add(data);
        var compiled = GraphTextInputs.compileJoin(data);
        assertEquals("abtext_3", compiled.join(port -> port));
        GraphTextInputs.remove(data, "b");
        assertEquals("abtext_3", compiled.join(port -> port));
        assertEquals("atext_3", GraphTextInputs.compileJoin(data).join(port -> port));
        assertEquals("a", compiled.join(port -> "a".equals(port) ? port : null));
        assertThrows(UnsupportedOperationException.class, () -> compiled.ports().clear());
    }

    @Test
    void preservesLegacyPortsAndJoinsNumericallyOrderedInputs(){
        CompoundTag data = new CompoundTag();
        assertEquals(List.of("a", "b"), GraphTextInputs.inputs(data).keySet().stream().toList());
        assertEquals("ab", GraphTextInputs.join(data, port -> port));
        for(int idx = 3; idx <= 12; idx++) assertEquals("text_" + idx, GraphTextInputs.add(data));
        assertEquals("abtext_3text_4text_5text_6text_7text_8text_9text_10text_11text_12",
                GraphTextInputs.join(data.copy(), port -> port));
        assertTrue(GraphTextInputs.remove(data, "text_4"));
        assertEquals("text_13", GraphTextInputs.add(data));
    }

    @Test
    void removesLegacyPortsAndRetainsOneInput(){
        CompoundTag data = new CompoundTag();
        CompoundTag defaults = new CompoundTag();
        defaults.putString("a", "old");
        defaults.putString("b", "retained");
        data.put("Defaults", defaults);
        assertTrue(GraphTextInputs.remove(data, "a"));
        assertEquals(List.of("b"), GraphTextInputs.inputs(data.copy()).keySet().stream().toList());
        assertFalse(data.getCompound("Defaults").contains("a"));
        assertEquals("retained", data.getCompound("Defaults").getString("b"));
        assertFalse(GraphTextInputs.remove(data, "b"));
        assertEquals("text_3", GraphTextInputs.add(data));
    }

    @Test
    void boundsInputsAndIgnoresNullText(){
        CompoundTag data = new CompoundTag();
        for(int idx = 2; idx < GraphTextInputs.MAX_INPUTS; idx++) GraphTextInputs.add(data);
        assertEquals("", GraphTextInputs.add(data));
        assertEquals(GraphTextInputs.MAX_INPUTS, GraphTextInputs.inputs(data).size());
        assertEquals("", GraphTextInputs.join(data, port -> null));
    }
}
