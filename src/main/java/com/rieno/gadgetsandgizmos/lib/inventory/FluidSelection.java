package com.rieno.gadgetsandgizmos.lib.inventory;

import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Resolve graph strings to stable registry IDs on both logical sides
public final class FluidSelection{
    public static final String ANY = "Any";
    private static final Map<String, Map<String, String>> ENGLISH = new HashMap<>();
    private static Map<String, String> names;
    private static Language language;
    private FluidSelection(){}

    // Empty/Any clears a selection; unknown or ambiguous display names leave it unchanged
    public static Optional<String> resolve(String value){
        String text = value == null ? "" : value.strip();
        if(text.isEmpty() || ANY.equalsIgnoreCase(text)) return Optional.of("");
        var id = ResourceLocation.tryParse(text);
        if(id != null && BuiltInRegistries.FLUID.containsKey(id)
                && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY) return Optional.of(id.toString());
        if(text.contains(":")) return Optional.empty();
        synchronized(FluidSelection.class){
            if(names == null || language != Language.getInstance()){
                language = Language.getInstance();
                names = new HashMap<>();
                for(var fluid : BuiltInRegistries.FLUID){
                    if(fluid == Fluids.EMPTY || fluid instanceof FlowingFluid flowing && flowing.getSource() != fluid) continue;
                    String key = BuiltInRegistries.FLUID.getKey(fluid).toString();
                    var name = new FluidStack(fluid, 1).getHoverName();
                    addName(name.getString(), key);
                    addName(englishName(name), key);
                }
            }
            String resolved = names.get(text.toLowerCase(Locale.ROOT));
            return resolved == null || resolved.isEmpty() ? Optional.empty() : Optional.of(resolved);
        }
    }

    private static void addName(String name, String id){
        names.merge(name.strip().toLowerCase(Locale.ROOT), id, (first, next) -> first.equals(next) ? first : "");
    }

    // Dedicated servers do not load client language packs; use the owning mod's bundled English fallback
    static String englishName(Component name){
        if(!(name.getContents() instanceof TranslatableContents translation)) return name.getString();
        String key = translation.getKey();
        int start = key.indexOf('.'), end = key.indexOf('.', start + 1);
        if(start < 0 || end < 0) return name.getString();
        String namespace = key.substring(start + 1, end);
        return ENGLISH.computeIfAbsent(namespace, FluidSelection::loadEnglish).getOrDefault(key, name.getString());
    }

    private static Map<String, String> loadEnglish(String namespace){
        Map<String, String> values = new HashMap<>();
        try(var stream = FluidSelection.class.getResourceAsStream("/assets/" + namespace + "/lang/en_us.json")){
            if(stream == null) return Map.of();
            try(var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)){
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                    if(entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()){
                        values.put(entry.getKey(), entry.getValue().getAsString());
                    }
                });
            }
        }catch(java.io.IOException | RuntimeException ignored){}
        return Map.copyOf(values);
    }
}
