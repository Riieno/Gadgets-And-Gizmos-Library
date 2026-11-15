package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import java.util.List;
import java.util.Map;

// Transfer a bounded immutable character terminal without exposing an engine implementation
public record ScriptTerminal(int width, int height, List<String> text, List<String> foreground, List<String> background,
                             List<Integer> palette, int cursorX, int cursorY, boolean blink, int cursorColor){
    public static final String PORT = "__lua_terminal";
    public ScriptTerminal{
        if(width < 1 || width > 160 || height < 1 || height > 80 || width * height > 8192) throw new IllegalArgumentException("Invalid terminal dimensions");
        text = List.copyOf(text); foreground = List.copyOf(foreground); background = List.copyOf(background); palette = List.copyOf(palette);
        if(text.size() != height || foreground.size() != height || background.size() != height || palette.size() != 16) throw new IllegalArgumentException("Invalid terminal rows");
        for(int idx = 0; idx < height; idx++){
            if(text.get(idx).length() != width || foreground.get(idx).length() != width || background.get(idx).length() != width
                    || !foreground.get(idx).matches("[0-9a-f]+") || !background.get(idx).matches("[0-9a-f]+")) throw new IllegalArgumentException("Invalid terminal row");
        }
        cursorColor = Math.clamp(cursorColor, 0, 15);
    }

    public GraphValue toValue(){
        return GraphValue.map(Map.of("width", width, "height", height, "text", text, "foreground", foreground, "background", background,
                "palette", palette, "cursorX", cursorX, "cursorY", cursorY, "blink", blink, "cursorColor", cursorColor));
    }

    // Adapt optional terminal implementations without exposing their classes through the library API
    public interface Access{
        int width(); int height();
        String text(int row); String foreground(int row); String background(int row); int color(int index);
        int cursorX(); int cursorY(); boolean blink(); int cursorColor();
    }

    public static ScriptTerminal capture(Access source){
        int width = source.width(), height = source.height();
        if(width < 1 || width > 160 || height < 1 || height > 80 || width * height > 8192) return null;
        var text = new java.util.ArrayList<String>(); var fg = new java.util.ArrayList<String>(); var bg = new java.util.ArrayList<String>(); var palette = new java.util.ArrayList<Integer>();
        for(int row = 0; row < height; row++){ text.add(source.text(row)); fg.add(source.foreground(row)); bg.add(source.background(row)); }
        for(int idx = 0; idx < 16; idx++) palette.add(source.color(idx));
        return new ScriptTerminal(width, height, text, fg, bg, palette, source.cursorX(), source.cursorY(), source.blink(), source.cursorColor());
    }

    // Preserve the native terminal frame contract used by external display renderers
    public net.minecraft.nbt.CompoundTag displayFrame(){
        var frame = new net.minecraft.nbt.CompoundTag();
        var terminal = new net.minecraft.nbt.CompoundTag();
        terminal.putInt("term_cursorX", cursorX); terminal.putInt("term_cursorY", cursorY);
        terminal.putBoolean("term_cursorBlink", blink); terminal.putInt("term_textColour", cursorColor); terminal.putInt("term_bgColour", 15);
        var lines = new net.minecraft.nbt.ListTag(); var foreground = new net.minecraft.nbt.ListTag(); var background = new net.minecraft.nbt.ListTag();
        for(int idx = 0; idx < height; idx++){
            terminal.putString("term_text_" + idx, text.get(idx)); terminal.putString("term_textColour_" + idx, this.foreground.get(idx));
            terminal.putString("term_textBgColour_" + idx, this.background.get(idx));
            lines.add(net.minecraft.nbt.StringTag.valueOf(text.get(idx))); foreground.add(net.minecraft.nbt.StringTag.valueOf(this.foreground.get(idx)));
            background.add(net.minecraft.nbt.StringTag.valueOf(this.background.get(idx)));
        }
        int[] rgb = new int[16];
        for(int idx = 0; idx < 16; idx++) rgb[idx] = palette.get(15 - idx) & 0xFFFFFF;
        terminal.putIntArray("term_palette", rgb);
        frame.putString("Format", "terminal"); frame.putString("Source", "CC:Tweaked"); frame.putBoolean("PreserveAspect", true); frame.putInt("Width", width); frame.putInt("Height", height);
        frame.put("Terminal", terminal); frame.put("Lines", lines); frame.put("Foreground", foreground); frame.put("Background", background);
        frame.putIntArray("Palette", palette.stream().mapToInt(Integer::intValue).toArray());
        return frame;
    }

    public static ScriptTerminal fromValue(GraphValue value){
        if(value == null) return null;
        try{
            if(!(ScriptValues.encode(value) instanceof Map<?, ?> data)) return null;
            return new ScriptTerminal(number(data, "width"), number(data, "height"), strings(data.get("text")), strings(data.get("foreground")),
                    strings(data.get("background")), ((List<?>)data.get("palette")).stream().map(val -> ((Number)val).intValue()).toList(),
                    number(data, "cursorX"), number(data, "cursorY"), Boolean.TRUE.equals(data.get("blink")), number(data, "cursorColor"));
        }catch(RuntimeException ignored){ return null; }
    }

    // Read native terminal transports without losing their palette or cell colours
    public static ScriptTerminal fromDisplayFrame(net.minecraft.nbt.CompoundTag frame){
        if(!"terminal".equals(frame.getString("Format"))) return null;
        try{
            int width = frame.getInt("Width"), height = frame.getInt("Height");
            if(width < 1 || width > 160 || height < 1 || height > 80 || width * height > 8192) return null;
            var nativeTag = frame.getCompound("Terminal");
            var lines = new java.util.ArrayList<String>(); var fg = new java.util.ArrayList<String>(); var bg = new java.util.ArrayList<String>();
            var palette = new java.util.ArrayList<Integer>();
            int[] supplied = frame.getIntArray("Palette"), nativePalette = nativeTag.getIntArray("term_palette");
            int[] standard = {0xF0F0F0,0xF2B233,0xE57FD8,0x99B2F2,0xDEDE6C,0x7FCC19,0xF2B2CC,0x4C4C4C,0x999999,0x4C99B2,0xB266E5,0x3366CC,0x7F664C,0x57A64E,0xCC4C4C,0x111111};
            for(int idx = 0; idx < 16; idx++) palette.add(supplied.length == 16 ? supplied[idx] : nativePalette.length == 16 ? nativePalette[15 - idx] : standard[idx]);
            for(int idx = 0; idx < height; idx++){
                lines.add(nativeTag.isEmpty() ? frame.getList("Lines", 8).getString(idx) : nativeTag.getString("term_text_" + idx));
                fg.add(nativeTag.isEmpty() ? frame.getList("Foreground", 8).getString(idx) : nativeTag.getString("term_textColour_" + idx));
                bg.add(nativeTag.isEmpty() ? frame.getList("Background", 8).getString(idx) : nativeTag.getString("term_textBgColour_" + idx));
            }
            return new ScriptTerminal(width, height, lines, fg, bg, palette, nativeTag.getInt("term_cursorX"), nativeTag.getInt("term_cursorY"),
                    nativeTag.getBoolean("term_cursorBlink"), nativeTag.getInt("term_textColour"));
        }catch(RuntimeException ex){ return null; }
    }

    private static int number(Map<?, ?> data, String key){ return ((Number)data.get(key)).intValue(); }
    private static List<String> strings(Object val){ return ((List<?>)val).stream().map(entry -> (String)entry).toList(); }
}
