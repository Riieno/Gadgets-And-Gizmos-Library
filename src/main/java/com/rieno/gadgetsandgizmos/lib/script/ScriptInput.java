package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;

// Validate terminal input independently of a host screen or optional computer engine
public record ScriptInput(String action, double x, double y, int value, String text){
    public ScriptInput(String action, double x, double y, int value){ this(action, x, y, value, ""); }
    public ScriptInput{
        if(!List.of("click", "drag", "release", "scroll", "key", "key_up", "char", "paste").contains(action)
                || !Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("Invalid terminal input");
        text = text == null ? "" : text;
        if(text.length() > 16384 || action.equals("char") && (value < 32 || value > 255)
                || List.of("click", "drag", "release").contains(action) && (value < 0 || value > 2)
                || List.of("key", "key_up").contains(action) && (value < 0 || value > 512))
            throw new IllegalArgumentException("Invalid terminal input value");
    }

    public String event(){ return switch(action){ case "click" -> "mouse_click"; case "drag" -> "mouse_drag"; case "release" -> "mouse_up"; case "scroll" -> "mouse_scroll"; default -> action; }; }
    public Object[] arguments(int width, int height){
        return switch(action){
            case "click", "drag", "release", "scroll" -> new Object[]{action.equals("scroll") ? Integer.signum(value) : value + 1,
                    Math.clamp((int)Math.floor(x * width) + 1, 1, width), Math.clamp((int)Math.floor(y * height) + 1, 1, height)};
            case "key" -> new Object[]{value, false};
            case "key_up" -> new Object[]{value};
            case "char" -> new Object[]{String.valueOf((char)value)};
            default -> new Object[]{text};
        };
    }

    // Map the same fitted character rectangle used when rendering a terminal
    public static double[] point(double x, double y, double width, double height, int columns, int rows){
        if(width <= 0 || height <= 0 || columns <= 0 || rows <= 0 || !Double.isFinite(width) || !Double.isFinite(height)
                || !Double.isFinite(x) || !Double.isFinite(y)) return null;
        return com.rieno.gadgetsandgizmos.lib.display.SurfaceFit.fit(columns * 6D, rows * 9D, width, height, true).point(x, y);
    }
}
