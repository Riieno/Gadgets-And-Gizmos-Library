package com.rieno.gadgetsandgizmos.lib.script;

// Keep configured and manually resized terminals within the shared frame transport limits
public record ScriptTerminalSize(int width, int height){
    public static final int MAX_WIDTH = 160, MAX_HEIGHT = 80, MAX_CELLS = 8192;
    public static final ScriptTerminalSize DEFAULT = new ScriptTerminalSize(51, 19);

    public ScriptTerminalSize{ validate(width, height); }
    public static boolean valid(int width, int height){
        return width >= 1 && height >= 1 && width <= MAX_WIDTH && height <= MAX_HEIGHT && (long)width * height <= MAX_CELLS;
    }
    public static void validate(int width, int height){
        if(!valid(width, height)) throw new IllegalArgumentException("Terminal size must be 1-160 columns by 1-80 rows, at most 8192 cells");
    }
    public static ScriptTerminalSize fit(int width, int height){
        width = Math.max(1, width); height = Math.max(1, height);
        double scale = Math.min(1, Math.min(MAX_WIDTH / (double)width,
                Math.min(MAX_HEIGHT / (double)height, Math.sqrt(MAX_CELLS / ((double)width * height)))));
        return new ScriptTerminalSize(Math.max(1, (int)Math.floor(width * scale)), Math.max(1, (int)Math.floor(height * scale)));
    }
}
