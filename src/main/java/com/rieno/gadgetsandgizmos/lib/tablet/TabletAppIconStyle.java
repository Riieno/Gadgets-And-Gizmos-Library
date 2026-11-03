package com.rieno.gadgetsandgizmos.lib.tablet;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

// Configure a tablet app's fallback square without replacing its registered artwork
public record TabletAppIconStyle(int color, ResourceLocation item, boolean forceFallback){
    public TabletAppIconStyle{
        color &= 0xFFFFFF;
        Objects.requireNonNull(item, "item");
    }

    // Preserve registered artwork unless a consuming mod requests a fallback
    public static TabletAppIconStyle defaults(TabletAppDefinition app){
        return new TabletAppIconStyle(app.accentColor(), ResourceLocation.withDefaultNamespace("paper"), false);
    }

    // Read partial overrides while keeping omitted fields from the supplied defaults
    public static TabletAppIconStyle fromJson(JsonObject object, TabletAppIconStyle fallback){
        if(object == null) return fallback;
        String hex = object.has("color") ? object.get("color").getAsString() : null;
        if(hex != null && !hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Icon color must be #RRGGBB");
        ResourceLocation item = object.has("item") ? ResourceLocation.tryParse(object.get("item").getAsString()) : fallback.item();
        if(item == null) throw new IllegalArgumentException("Invalid icon item id");
        if(object.has("force_fallback") && (!object.get("force_fallback").isJsonPrimitive()
                || !object.getAsJsonPrimitive("force_fallback").isBoolean())) throw new IllegalArgumentException("force_fallback must be a boolean");
        return new TabletAppIconStyle(hex == null ? fallback.color() : Integer.parseInt(hex.substring(1), 16), item,
                object.has("force_fallback") ? object.get("force_fallback").getAsBoolean() : fallback.forceFallback());
    }

    public JsonObject toJson(){
        JsonObject res = new JsonObject();
        res.addProperty("color", String.format(Locale.ROOT, "#%06X", color));
        res.addProperty("item", item.toString());
        res.addProperty("force_fallback", forceFallback);
        return res;
    }

    public CompoundTag toTag(){
        CompoundTag res = new CompoundTag();
        res.putInt("Color", color);
        res.putString("Item", item.toString());
        res.putBoolean("ForceFallback", forceFallback);
        return res;
    }

    public static TabletAppIconStyle fromTag(CompoundTag tag){
        ResourceLocation item = ResourceLocation.tryParse(tag.getString("Item"));
        return new TabletAppIconStyle(tag.getInt("Color"), item == null ? ResourceLocation.withDefaultNamespace("paper") : item,
                tag.getBoolean("ForceFallback"));
    }
}
