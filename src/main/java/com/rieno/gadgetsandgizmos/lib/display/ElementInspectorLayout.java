package com.rieno.gadgetsandgizmos.lib.display;

// Share property rows between element inspector rendering and input handling
public record ElementInspectorLayout(boolean hasFontControls, boolean pairedStyles,
                                     int fontSizeOffset, int colorOffset, int fontStyleOffset){
    public static ElementInspectorLayout forType(String type){
        return switch(type == null ? "" : type){
            case "value" -> new ElementInspectorLayout(true, false, 65, 84, 144);
            case "text" -> new ElementInspectorLayout(true, false, 49, 68, 128);
            case "button", "toggle", "text_input" ->
                    new ElementInspectorLayout(true, true, 49, 68, 128);
            default -> new ElementInspectorLayout(false, false, 49, 0, 128);
        };
    }

    public int colorLabelOffset(String key){
        return hasFontControls ? colorOffset : "BorderColor".equals(key) ? 46 : 0;
    }

    public int fontStyleWidth(int contentWidth){
        return Math.max(1, pairedStyles ? (contentWidth - 8) / 2 : contentWidth);
    }
}
