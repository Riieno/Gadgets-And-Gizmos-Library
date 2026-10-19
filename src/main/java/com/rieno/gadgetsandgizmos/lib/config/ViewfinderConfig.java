package com.rieno.gadgetsandgizmos.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Persist each client's viewfinder preferences independently from camera and graph data
public final class ViewfinderConfig{
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FRAME;
    public static final ModConfigSpec.BooleanValue GRID;

    static{
        var builder = new ModConfigSpec.Builder();
        builder.push("camera");
        FRAME = builder.comment("Show the camcorder frame while controlling a camera.").define("showFrame", true);
        GRID = builder.comment("Show a rule-of-thirds grid while controlling a camera.").define("showGrid", false);
        builder.pop();
        SPEC = builder.build();
    }
    private ViewfinderConfig(){}
    public static void toggleFrame(){ FRAME.set(!FRAME.get()); SPEC.save(); }
    public static void toggleGrid(){ GRID.set(!GRID.get()); SPEC.save(); }
}
