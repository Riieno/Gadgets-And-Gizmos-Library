package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// Export a packed ARGB color alongside its RGB channels and opacity
public final class ColorGraphNode{
    // Keep the existing color port before the component outputs
    public static final Map<String, String> OUTPUTS = GraphPortOrder.ports(
            "value", "color", "hex", "string", "rgb", "list", "alpha", "number");

    private ColorGraphNode(){}

    // Share one color value across the packed, hex, channel list and alpha outputs
    public static Map<String, GraphValue> execute(int color){
        return Map.of("value", GraphValue.number(color),
                "hex", GraphValue.string(String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF)),
                "rgb", GraphValue.list(List.of(color >>> 16 & 0xFF, color >>> 8 & 0xFF, color & 0xFF)),
                "alpha", GraphValue.number(color >>> 24 & 0xFF));
    }
}
