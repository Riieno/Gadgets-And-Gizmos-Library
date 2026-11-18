package com.rieno.gadgetsandgizmos.lib.script;

// Preserve graph ARGB colors at the Lua boundary without confusing packed values with CC color masks
public final class ScriptHexColors{
    private ScriptHexColors(){}
    public static String format(int argb){ return String.format(java.util.Locale.ROOT, "#%08X", argb); }
    public static int parse(String text){
        if(text == null || !text.startsWith("#") || text.length() != 7 && text.length() != 9) throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB");
        for(int idx = 1; idx < text.length(); idx++) if(Character.digit(text.charAt(idx), 16) < 0) throw new IllegalArgumentException("Invalid hex color");
        try{ return (int)Long.parseLong(text.substring(1), 16) | (text.length() == 7 ? 0xFF000000 : 0); }
        catch(NumberFormatException ex){ throw new IllegalArgumentException("Invalid hex color"); }
    }
}
