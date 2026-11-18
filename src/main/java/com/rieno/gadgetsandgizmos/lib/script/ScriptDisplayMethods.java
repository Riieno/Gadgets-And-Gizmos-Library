package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;

// Preserve terminal query shapes while display mutations produce no rendering or frames
public final class ScriptDisplayMethods{
    private int x = 1, y = 1, foreground = 1, background = 32768;
    private boolean blink;
    private double scale = 1;

    public void bind(ScriptMethods methods){
        for(String name : List.of("write", "blit", "blitHex", "clear", "clearLine", "scroll")) methods.bind(name, args -> new Object[0]);
        methods.bind("getSize", args -> new Object[]{51, 19});
        methods.bind("getCursorPos", args -> new Object[]{x, y});
        methods.bind("setCursorPos", args -> { x = number(args, 0).intValue(); y = number(args, 1).intValue(); return new Object[0]; });
        methods.bind("getCursorBlink", args -> new Object[]{blink});
        methods.bind("setCursorBlink", args -> { if(args.isEmpty() || !(args.getFirst() instanceof Boolean)) throw new ScriptException("Expected a boolean"); blink = (Boolean)args.getFirst(); return new Object[0]; });
        for(String name : List.of("getTextColor", "getTextColour")) methods.bind(name, args -> new Object[]{foreground});
        for(String name : List.of("setTextColor", "setTextColour")) methods.bind(name, args -> { foreground = color(args); return new Object[0]; });
        for(String name : List.of("getBackgroundColor", "getBackgroundColour")) methods.bind(name, args -> new Object[]{background});
        for(String name : List.of("setBackgroundColor", "setBackgroundColour")) methods.bind(name, args -> { background = color(args); return new Object[0]; });
        for(String name : List.of("isColor", "isColour")) methods.bind(name, args -> new Object[]{true});
        for(String name : List.of("getPaletteColor", "getPaletteColour")) methods.bind(name, args -> {
            int rgb = PALETTE[Integer.numberOfTrailingZeros(color(args))]; return new Object[]{(rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0};
        });
        for(String name : List.of("setPaletteColor", "setPaletteColour")) methods.bind(name, args -> new Object[0]);
        methods.bind("getTextScale", args -> new Object[]{scale});
        methods.bind("setTextScale", args -> { scale = number(args, 0).doubleValue(); return new Object[0]; });
    }
    private static Number number(List<Object> args, int idx){
        if(idx >= args.size() || !(args.get(idx) instanceof Number num) || !Double.isFinite(num.doubleValue())) throw new ScriptException("Expected a finite number");
        return (Number)args.get(idx);
    }
    private static int color(List<Object> args){
        int value = number(args, 0).intValue();
        if(value < 1 || value > 32768 || Integer.bitCount(value) != 1) throw new ScriptException("Invalid colour");
        return value;
    }
    private static final int[] PALETTE = {0xF0F0F0, 0xF2B233, 0xE57FD8, 0x99B2F2, 0xDEDE6C, 0x7FCC19, 0xF2B2CC, 0x4C4C4C,
            0x999999, 0x4C99B2, 0xB266E5, 0x3366CC, 0x7F664C, 0x57A64E, 0xCC4C4C, 0x111111};
}
