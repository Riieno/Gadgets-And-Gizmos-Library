package com.rieno.gadgetsandgizmos.lib.tablet;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TabletAppIconStyleTest{
    private static final TabletAppIconStyle DEFAULT = new TabletAppIconStyle(0xAB1234, ResourceLocation.parse("minecraft:chest"), true);

    @Test void partialSettingsKeepTheConfiguredItemAndColor(){
        var style = TabletAppIconStyle.fromJson(JsonParser.parseString("{\"force_fallback\":false}").getAsJsonObject(), DEFAULT);
        assertEquals(0xAB1234, style.color());
        assertEquals(DEFAULT.item(), style.item());
        assertFalse(style.forceFallback());
        assertEquals(DEFAULT, TabletAppIconStyle.fromJson(null, DEFAULT));
    }

    @Test void editableSettingsSurviveJsonAndNetworkTags(){
        assertEquals(DEFAULT, TabletAppIconStyle.fromJson(DEFAULT.toJson(), TabletAppIconStyle.defaults(app())));
        assertEquals(DEFAULT, TabletAppIconStyle.fromTag(DEFAULT.toTag()));
        assertEquals(0x123456, new TabletAppIconStyle(0xFF123456, DEFAULT.item(), false).color());
        for(String json : List.of("{\"color\":\"pink\"}", "{\"item\":\"invalid id\"}", "{\"force_fallback\":\"false\"}")){
            assertThrows(IllegalArgumentException.class,
                    () -> TabletAppIconStyle.fromJson(JsonParser.parseString(json).getAsJsonObject(), DEFAULT));
        }
    }

    @Test void serverChangesReplaceTheWholeSnapshotAndLeaveExistingArtworkAvailable(){
        var app = app();
        try{
            Map<ResourceLocation, TabletAppIconStyle> values = new HashMap<>(Map.of(app.id(), DEFAULT));
            TabletAppIconStyles.apply(values);
            values.clear();
            assertEquals(DEFAULT, TabletAppIconStyles.style(app));
            TabletAppIconStyles.apply(Map.of());
            assertFalse(TabletAppIconStyles.style(app).forceFallback());
            assertEquals(0x123456, TabletAppIconStyles.style(app).color());
        }finally{ TabletAppIconStyles.clear(); }
    }

    private static TabletAppDefinition app(){
        return new TabletAppDefinition(ResourceLocation.parse("test:icon"), Component.literal("Icon"), Component.empty(), 0xFF123456, List.of());
    }
}
