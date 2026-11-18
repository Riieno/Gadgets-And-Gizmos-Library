package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import java.util.List;
import java.util.Map;

// Transfer a bounded immutable character terminal without exposing an engine implementation
public record ScriptTerminal(int width, int height, List<String> text, List<String> foreground, List<String> background,
                             List<Integer> palette, int cursorX, int cursorY, boolean blink, int cursorColor,
                             List<String> foregroundHex, List<String> backgroundHex, String cursorHex){
    public static final String PORT = "__lua_terminal";
    public ScriptTerminal(int width, int height, List<String> text, List<String> foreground, List<String> background,
                          List<Integer> palette, int cursorX, int cursorY, boolean blink, int cursorColor){
        this(width, height, text, foreground, background, palette, cursorX, cursorY, blink, cursorColor, List.of(), List.of(), "");
    }
    public ScriptTerminal{
        ScriptTerminalSize.validate(width, height);
        text = List.copyOf(text); foreground = List.copyOf(foreground); background = List.copyOf(background); palette = List.copyOf(palette);
        if(text.size() != height || foreground.size() != height || background.size() != height || palette.size() != 16) throw new IllegalArgumentException("Invalid terminal rows");
        for(int idx = 0; idx < height; idx++){
            if(text.get(idx).length() != width || foreground.get(idx).length() != width || background.get(idx).length() != width
                    || !colors(foreground.get(idx)) || !colors(background.get(idx))) throw new IllegalArgumentException("Invalid terminal row");
        }
        cursorColor = Math.clamp(cursorColor, 0, 15);
        foregroundHex = hexRows(foregroundHex, width, height); backgroundHex = hexRows(backgroundHex, width, height);
        cursorHex = cursorHex == null ? "" : cursorHex;
        if(!cursorHex.isEmpty() && (cursorHex.length() != 8 || !colors(cursorHex.toLowerCase(java.util.Locale.ROOT)))) throw new IllegalArgumentException("Invalid cursor color");
    }

    public GraphValue toValue(){
        var data = new java.util.LinkedHashMap<String, Object>(Map.of("width", width, "height", height, "text", text, "foreground", foreground, "background", background,
                "palette", palette, "cursorX", cursorX, "cursorY", cursorY, "blink", blink, "cursorColor", cursorColor));
        if(hasHexColors()){ data.put("foregroundHex", foregroundHex); data.put("backgroundHex", backgroundHex); data.put("cursorHex", cursorHex); }
        return GraphValue.map(data);
    }
    public boolean hasHexColors(){ return !foregroundHex.isEmpty() || !backgroundHex.isEmpty() || !cursorHex.isEmpty(); }
    public int foregroundArgb(int row, int col){ return argb(foregroundHex, foreground, row, col); }
    public int backgroundArgb(int row, int col){ return argb(backgroundHex, background, row, col); }
    public int cursorArgb(){ return cursorHex.isEmpty() ? palette.get(cursorColor) | 0xFF000000 : (int)Long.parseLong(cursorHex, 16); }
    private int argb(List<String> hex, List<String> indices, int row, int col){
        String cell = hex.isEmpty() ? "--------" : hex.get(row).substring(col * 8, col * 8 + 8);
        return cell.equals("--------") ? palette.get(Character.digit(indices.get(row).charAt(col), 16)) | 0xFF000000 : (int)Long.parseLong(cell, 16);
    }
    private static List<String> hexRows(List<String> rows, int width, int height){
        if(rows == null || rows.isEmpty()) return List.of();
        rows = rows.stream().map(row -> row.toLowerCase(java.util.Locale.ROOT)).toList();
        if(rows.size() != height) throw new IllegalArgumentException("Invalid hex terminal rows");
        for(String row : rows){
            if(row.length() != width * 8) throw new IllegalArgumentException("Invalid hex terminal width");
            for(int col = 0; col < width; col++){
                String cell = row.substring(col * 8, col * 8 + 8);
                if(!cell.equals("--------") && !colors(cell)) throw new IllegalArgumentException("Invalid hex terminal color");
            }
        }
        return rows;
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
        return displayFrame("CC:Tweaked");
    }

    public net.minecraft.nbt.CompoundTag displayFrame(String source){
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
        frame.putString("Format", "terminal"); frame.putString("Source", source); frame.putBoolean("PreserveAspect", true); frame.putInt("Width", width); frame.putInt("Height", height);
        frame.put("Terminal", terminal); frame.put("Lines", lines); frame.put("Foreground", foreground); frame.put("Background", background);
        frame.putIntArray("Palette", palette.stream().mapToInt(Integer::intValue).toArray());
        if(hasHexColors()){
            var colors = new TerminalColors(width, height);
            colors.foreground(direct(cursorHex));
            for(int row = 0; row < height; row++) for(int col = 0; col < width; col++){
                colors.paint(col, row, 1, direct(foregroundHex, row, col), direct(backgroundHex, row, col));
            }
            terminal.putByteArray("gadgetsngizmos:terminal_colors", colors.encode());
            var fgHex = new net.minecraft.nbt.ListTag(); var bgHex = new net.minecraft.nbt.ListTag();
            foregroundHex.forEach(row -> fgHex.add(net.minecraft.nbt.StringTag.valueOf(row)));
            backgroundHex.forEach(row -> bgHex.add(net.minecraft.nbt.StringTag.valueOf(row)));
            frame.put("ForegroundHex", fgHex); frame.put("BackgroundHex", bgHex); frame.putString("CursorHex", cursorHex);
        }
        return frame;
    }

    private static long direct(List<String> rows, int row, int col){
        return rows.isEmpty() ? TerminalColors.PALETTE : direct(rows.get(row).substring(col * 8, col * 8 + 8));
    }
    private static long direct(String hex){ return hex.isEmpty() || hex.equals("--------") ? TerminalColors.PALETTE : Long.parseLong(hex, 16); }

    public static ScriptTerminal fromValue(GraphValue value){
        if(value == null) return null;
        try{
            if(!(value.value() instanceof Map<?, ?> data)) return null;
            return fromData(data);
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
            var colors = new TerminalColors(width, height);
            colors.decode(nativeTag.getByteArray("gadgetsngizmos:terminal_colors"));
            return new ScriptTerminal(width, height, lines, fg, bg, palette, nativeTag.getInt("term_cursorX"), nativeTag.getInt("term_cursorY"),
                    nativeTag.getBoolean("term_cursorBlink"), nativeTag.getInt("term_textColour"),
                    frame.contains("ForegroundHex") ? frame.getList("ForegroundHex", 8).stream().map(net.minecraft.nbt.Tag::getAsString).toList() : colors.rows(true),
                    frame.contains("BackgroundHex") ? frame.getList("BackgroundHex", 8).stream().map(net.minecraft.nbt.Tag::getAsString).toList() : colors.rows(false),
                    frame.contains("CursorHex") ? frame.getString("CursorHex") : colors.foreground() == TerminalColors.PALETTE ? "" : TerminalColors.hex(colors.foreground()));
        }catch(RuntimeException ex){ return null; }
    }

    public static ScriptTerminal fromData(Map<?, ?> data){
        ScriptTerminalSize.validate(number(data,"width"), number(data,"height"));
        return new ScriptTerminal(number(data,"width"), number(data,"height"), rows(data.get("text")), rows(data.get("foreground")), rows(data.get("background")),
                entries(data.get("palette")).stream().map(val -> ((Number)unwrap(val)).intValue()).toList(), number(data,"cursorX"), number(data,"cursorY"),
                Boolean.TRUE.equals(unwrap(data.get("blink"))), number(data,"cursorColor"), rows(data.get("foregroundHex")), rows(data.get("backgroundHex")), java.util.Objects.toString(unwrap(data.get("cursorHex")), ""));
    }
    private static List<String> rows(Object data){ return entries(data).stream().map(val -> (String)unwrap(val)).toList(); }
    private static List<?> entries(Object data){
        data = unwrap(data);
        if(data == null) return List.of();
        if(data instanceof List<?> list){ if(list.size() > 80) throw new IllegalArgumentException("Invalid terminal array"); return list; }
        if(!(data instanceof Map<?,?> map) || map.size() > 80) throw new IllegalArgumentException("Invalid terminal array");
        return map.entrySet().stream().sorted(java.util.Comparator.comparingDouble(entry -> ((Number)entry.getKey()).doubleValue())).map(Map.Entry::getValue).toList();
    }
    private static Object unwrap(Object val){ while(val instanceof GraphValue graph) val = graph.value(); return val; }
    private static int number(Map<?, ?> data, String key){ return ((Number)unwrap(data.get(key))).intValue(); }
    private static boolean colors(String text){
        for(int idx = 0; idx < text.length(); idx++){
            char ch = text.charAt(idx);
            if((ch < '0' || ch > '9') && (ch < 'a' || ch > 'f')) return false;
        }
        return true;
    }
}
