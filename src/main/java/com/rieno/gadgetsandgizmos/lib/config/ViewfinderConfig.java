package com.rieno.gadgetsandgizmos.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewfinderPreferences;
import java.util.List;

// Persist each client's viewfinder preferences independently from camera and graph data
public final class ViewfinderConfig{
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FRAME;
    public static final ModConfigSpec.BooleanValue GRID;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SOURCES;
    private static final ViewfinderPreferences PREFERENCES = new ViewfinderPreferences();
    private static List<? extends String> loaded;

    static{
        var builder = new ModConfigSpec.Builder();
        builder.push("camera");
        FRAME = builder.comment("Show the camcorder frame while controlling a camera.").define("showFrame", true);
        GRID = builder.comment("Show a rule-of-thirds grid while controlling a camera.").define("showGrid", false);
        SOURCES = builder.comment("Independent frame and grid settings for each camera source.")
                .defineListAllowEmpty("sources", List.of(), entry -> entry instanceof String);
        builder.pop();
        SPEC = builder.build();
    }
    private ViewfinderConfig(){}
    public static void toggleFrame(){ FRAME.set(!FRAME.get()); SPEC.save(); }
    public static void toggleGrid(){ GRID.set(!GRID.get()); SPEC.save(); }
    // Existing global values remain defaults for cameras without an override
    public static boolean frame(ViewReference source){ return settings(source).frame(); }
    public static boolean grid(ViewReference source){ return settings(source).grid(); }
    public static void toggleFrame(ViewReference source){
        if(source == null) return;
        var previous = settings(source);
        save(source, new ViewfinderPreferences.Settings(!previous.frame(), previous.grid()));
    }
    public static void toggleGrid(ViewReference source){
        if(source == null) return;
        var previous = settings(source);
        save(source, new ViewfinderPreferences.Settings(previous.frame(), !previous.grid()));
    }
    private static ViewfinderPreferences.Settings settings(ViewReference source){
        List<? extends String> entries = SOURCES.get();
        if(loaded != entries){ PREFERENCES.read(entries); loaded = entries; }
        return PREFERENCES.get(source, new ViewfinderPreferences.Settings(FRAME.get(), GRID.get()));
    }
    private static void save(ViewReference source, ViewfinderPreferences.Settings settings){
        PREFERENCES.set(source, settings);
        loaded = PREFERENCES.write();
        SOURCES.set(loaded);
        SPEC.save();
    }
}
