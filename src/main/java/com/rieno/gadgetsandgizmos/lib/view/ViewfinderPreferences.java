package com.rieno.gadgetsandgizmos.lib.view;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Retain independent client preferences for each source without changing camera data
public final class ViewfinderPreferences{
    private final Map<ViewReference, Settings> sources = new LinkedHashMap<>();

    public record Settings(boolean frame, boolean grid){}

    public Settings get(@Nullable ViewReference source, Settings defaults){
        return source == null ? defaults : sources.getOrDefault(source, defaults);
    }

    public void set(ViewReference source, Settings settings){
        sources.put(java.util.Objects.requireNonNull(source), java.util.Objects.requireNonNull(settings));
    }

    // Ignore invalid entries without discarding the remaining cameras
    public void read(List<? extends String> entries){
        sources.clear();
        for(String entry : entries){
            if(entry == null) continue;
            try{
                var tag = TagParser.parseTag(entry);
                ViewReference source = ViewReference.fromTag(tag);
                if(source != null && tag.contains("Frame", Tag.TAG_BYTE) && tag.contains("Grid", Tag.TAG_BYTE)){
                    set(source, new Settings(tag.getBoolean("Frame"), tag.getBoolean("Grid")));
                }
            }catch(CommandSyntaxException | IllegalArgumentException ignored){}
        }
    }

    public List<String> write(){
        return sources.entrySet().stream().map(entry -> {
            var tag = entry.getKey().toTag();
            tag.putBoolean("Frame", entry.getValue().frame());
            tag.putBoolean("Grid", entry.getValue().grid());
            return tag.toString();
        }).toList();
    }
}
