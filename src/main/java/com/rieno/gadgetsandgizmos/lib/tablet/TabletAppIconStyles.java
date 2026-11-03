package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// Hold the complete presentation settings received from the current server
public final class TabletAppIconStyles{
    private static volatile Map<ResourceLocation, TabletAppIconStyle> styles = Map.of();

    private TabletAppIconStyles(){}

    public static void apply(Map<ResourceLocation, TabletAppIconStyle> values){
        styles = Map.copyOf(values);
    }

    public static TabletAppIconStyle style(TabletAppDefinition app){
        return styles.getOrDefault(app.id(), TabletAppIconStyle.defaults(app));
    }

    public static void clear(){ styles = Map.of(); }
}
