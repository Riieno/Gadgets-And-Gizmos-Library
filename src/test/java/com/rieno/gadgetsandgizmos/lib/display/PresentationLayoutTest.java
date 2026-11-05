package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

// Verify presentation definitions remain detached from runtime-owned mutable data
class PresentationLayoutTest{
    // Preserve element identity and defaults without adding graph document fields
    @Test
    void layoutRoundTripsWithoutGraphTopology(){
        CompoundTag data = new CompoundTag(); data.putString("Label", "HUD");
        var element = new PresentationLayout.Element("widget", "text", "Speed", data);
        data.putString("Label", "changed");
        CompoundTag tag = PresentationLayout.write(List.of(element));
        assertFalse(tag.contains("Nodes") || tag.contains("Edges") || tag.contains("Functions"));
        var decoded = PresentationLayout.read(tag).getFirst();
        assertEquals("widget", decoded.id());
        assertEquals("text", decoded.type());
        assertEquals("Speed", decoded.label());
        assertEquals("HUD", decoded.data().getString("Label"));
        decoded.data().putString("Label", "caller change");
        assertEquals("HUD", decoded.data().getString("Label"));
    }
}
