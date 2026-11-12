package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify packed colors retain channel order, leading zeroes and transparent alpha
class ColorGraphNodeTest{
    // Keep opacity separate from the RGB hex string and channel list
    @Test
    void translucentColorExportsAllComponents(){
        var outputs = ColorGraphNode.execute(0x8012AB03);
        assertEquals(0x8012AB03, (int)outputs.get("value").asNumber());
        assertEquals("#12AB03", outputs.get("hex").asString());
        assertEquals(List.of(18D, 171D, 3D), outputs.get("rgb").entries().stream().map(GraphValue::asNumber).toList());
        assertEquals(128, outputs.get("alpha").asNumber());
    }

    // Preserve invisible RGB channels instead of treating zero alpha as a missing value
    @Test
    void transparentAndOpaqueEndpointsKeepTheirChannels(){
        var transparent = ColorGraphNode.execute(0x00010203);
        assertEquals("#010203", transparent.get("hex").asString());
        assertEquals(0, transparent.get("alpha").asNumber());
        var white = ColorGraphNode.execute(0xFFFFFFFF);
        assertEquals(-1, white.get("value").asNumber());
        assertEquals("#FFFFFF", white.get("hex").asString());
        assertEquals(255, white.get("alpha").asNumber());
    }
}
