package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorStopEditor;
import com.rieno.gadgetsandgizmos.lib.display.PortPropertyBindings;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Preserve image assets, shared parameter bindings and reproducible gradient samples
class GraphVisualValuesTest{
    @Test void samplesIncludeBothEndpointsAndOpaqueColors(){
        var data = new CompoundTag();
        data.put(GradientGraphNode.PRESET, ColorEffectPreset.fromColors(List.of(0xFF0000, 0x0000FF), false).write());
        var output = GradientGraphNode.execute(data, 50);
        assertEquals(0xFF800080, (int)output.get("color").asNumber());
        var samples = output.get("gradient");
        assertEquals(100, ((Map<?, ?>)samples.value()).size());
        assertEquals(0xFFFF0000, (int)samples.member("0").asNumber());
        assertEquals(0xFF0000FF, (int)samples.member("99").asNumber());
        assertEquals("99", new ArrayList<>(((Map<?, ?>)samples.value()).keySet()).getLast());
        assertEquals(0xFFFF0000, (int)GradientGraphNode.execute(data, -20).get("color").asNumber());
        assertEquals(0xFF0000FF, (int)GradientGraphNode.execute(data, 150).get("color").asNumber());
    }

    @Test void clickedStopsRetainTheExistingGradientAndFixedEndpoints(){
        var editor = new ColorStopEditor(ColorEffectPreset.fromColors(List.of(0xFF0000, 0x0000FF), false));
        int color = editor.preset().samplePosition(.25F);
        assertTrue(editor.add(.25F));
        assertEquals(.25F, editor.preset().stops().get(editor.selected()).position());
        assertEquals(color, editor.preset().stops().get(editor.selected()).color());
        assertFalse(editor.add(.25F));
        assertTrue(editor.remove());
        editor.select(0); assertFalse(editor.remove()); assertFalse(editor.move(.5F));
        editor.select(1); assertFalse(editor.remove());
        assertFalse(editor.add(Float.NaN));
    }

    @Test void repeatedBindingsCanBeChangedAndClearedIndependently(){
        var first = PortPropertyBindings.assignProperty(Map.of("Color", "color"), "X", "position");
        var shared = PortPropertyBindings.assignProperty(first, "Y", "position");
        assertEquals(Map.of("X", "position", "Y", "position", "Color", "color"), shared);
        assertEquals(Map.of("Y", "position", "Color", "color"), PortPropertyBindings.assignProperty(shared, "X", ""));
        assertEquals(3, shared.size());
    }

    @Test void imageValuesDetachTheirMetadataAndRetainTheSource(){
        var chunks = new ArrayList<>(List.of("encoded image"));
        var image = GraphImageValue.of("graph-image:asset", Map.of("chunks", chunks));
        chunks.clear();
        assertEquals("image", image.type());
        assertEquals("graph-image:asset", GraphImageValue.source(image));
        assertEquals(1, image.member("chunks").entries().size());
        assertEquals("https://example.test/image.png", GraphImageValue.source(GraphValue.string("https://example.test/image.png")));
    }
}
