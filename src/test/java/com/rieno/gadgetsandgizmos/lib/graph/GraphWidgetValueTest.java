package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Preserve resolved widget snapshots independently of their mutable authoring data
class GraphWidgetValueTest{
    @Test void capturedCanvasDetachesBindingsElementsAndNativeImageTags(){
        var data = new CompoundTag(); data.putInt("WidgetWidth", 200); data.putInt("WidgetHeight", 100);
        data.putString("Target", "host-only");
        var appearance = new CompoundTag(); appearance.putBoolean("Display", true); appearance.putInt("TaskbarHeight", 14);
        data.put(GraphWidgetValue.APPEARANCE, appearance);
        var elements = new ListTag(); var elm = new CompoundTag(); elm.putString("Type", "image");
        elm.putInt("X", 4); elm.putInt("W", 50); elm.putInt("H", 30); elements.add(elm); data.put("WidgetElements", elements);
        var values = new CompoundTag(); var image = new CompoundTag();
        image.putByteArray("Bytes", new byte[]{1, 2, 3}); image.putBoolean("Embedded", true); values.put("picture", image);
        var widget = GraphWidgetValue.capture(data, values, true, val -> { val.putInt("X", 12); return val; });
        elm.putInt("X", 90); image.putBoolean("Embedded", false); values.remove("picture");
        var captured = GraphWidgetValue.payload(widget);
        assertFalse(captured.contains("Target"));
        assertTrue(captured.getBoolean("Constrain"));
        appearance.putInt("TaskbarHeight", 0);
        assertEquals(14, captured.getCompound(GraphWidgetValue.APPEARANCE).getInt("TaskbarHeight"));
        assertEquals(12, captured.getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0).getInt("X"));
        assertTrue(captured.getCompound(GraphWidgetValue.VALUES).getCompound("picture").getBoolean("Embedded"));
        assertArrayEquals(new byte[]{1, 2, 3}, captured.getCompound(GraphWidgetValue.VALUES).getCompound("picture").getByteArray("Bytes"));
        captured.getList("WidgetElements", Tag.TAG_COMPOUND).clear();
        assertEquals(1, GraphWidgetValue.payload(widget).getList("WidgetElements", Tag.TAG_COMPOUND).size());
        assertEquals("200 x 100 - 1 element", GraphWidgetValue.summary(widget));
    }

    @Test void widgetsRemainRichValuesInsideGenericAndCollectionPorts(){
        var widget = GraphWidgetValue.capture(new CompoundTag(), new CompoundTag(), val -> val);
        var inspection = new GraphPortInspection("Widget", "any", true, widget, null);
        assertEquals("widget", inspection.resolvedType());
        assertEquals(GraphPortInspection.Preview.WIDGET, inspection.preview());
        assertEquals("320 x 180 - 0 elements", inspection.summary());
        assertFalse(GraphValueOutline.isCollection(widget));
        var outline = new GraphValueOutline();
        var root = GraphValue.map(Map.of("display", widget));
        assertFalse(outline.snapshot(root, 10).rows().get(1).collection());
        assertEquals(widget, GraphValueOutline.resolve(root, List.of("display")));
        assertEquals("widget", GraphPortInspection.primaryOutput(Map.of("value", "boolean", "widget", "widget", "player", "player")));
        assertTrue(GraphWidgetValue.isGeneratedOutput(Map.of("widget", "widget", "value", "boolean"), "widget"));
        assertFalse(GraphWidgetValue.isGeneratedOutput(Map.of("value", "widget"), "value"));
        assertFalse(GraphWidgetValue.isGeneratedOutput(Map.of("widget", "boolean"), "widget"));
    }
}
