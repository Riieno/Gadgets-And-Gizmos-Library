package com.rieno.gadgetsandgizmos.lib.config;

import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminalSize;
import net.neoforged.neoforge.common.ModConfigSpec;

// Configure fallback terminal defaults without changing native ComputerCraft installations
public final class LuaRuntimeConfig{
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue TERMINAL_WIDTH, TERMINAL_HEIGHT, MONITOR_WIDTH, MONITOR_HEIGHT;
    static{
        var builder = new ModConfigSpec.Builder();
        builder.comment("LuaJ character dimensions; terminal.attach() or term.attach() still controls visibility.",
                "Sizes are bounded to 160 columns, 80 rows and 8192 cells.").push("lua");
        TERMINAL_WIDTH = builder.comment("Default terminal columns for new LuaJ instances. CC:Tweaked defaults to 51.")
                .defineInRange("terminalWidth", 51, 1, ScriptTerminalSize.MAX_WIDTH);
        TERMINAL_HEIGHT = builder.comment("Default terminal rows for new LuaJ instances. CC:Tweaked defaults to 19.")
                .defineInRange("terminalHeight", 19, 1, ScriptTerminalSize.MAX_HEIGHT);
        MONITOR_WIDTH = builder.comment("Override LuaJ monitor columns at text scale 1. Zero uses the host's normal CC:Tweaked display sizing.")
                .defineInRange("monitorWidth", 0, 0, ScriptTerminalSize.MAX_WIDTH);
        MONITOR_HEIGHT = builder.comment("Override LuaJ monitor rows at text scale 1. Zero uses the host's normal CC:Tweaked display sizing.")
                .defineInRange("monitorHeight", 0, 0, ScriptTerminalSize.MAX_HEIGHT);
        builder.pop(); SPEC = builder.build();
    }
    private LuaRuntimeConfig(){}

    public static ScriptTerminalSize terminalSize(){
        return ScriptTerminalSize.fit(SPEC.isLoaded() ? TERMINAL_WIDTH.get() : 51, SPEC.isLoaded() ? TERMINAL_HEIGHT.get() : 19);
    }
    public static ScriptTerminalSize monitorSize(int width, int height){
        int columns = SPEC.isLoaded() ? MONITOR_WIDTH.get() : 0, rows = SPEC.isLoaded() ? MONITOR_HEIGHT.get() : 0;
        return ScriptTerminalSize.fit(columns == 0 ? width : columns, rows == 0 ? height : rows);
    }
}
