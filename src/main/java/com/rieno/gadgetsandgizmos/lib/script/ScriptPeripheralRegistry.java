package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

// Register optional device adapters under the owning mod's namespace
public final class ScriptPeripheralRegistry{
    @FunctionalInterface public interface Factory{ ScriptPeripheralBinding resolve(BlockEntity target, Direction side); }
    private record Entry(ResourceLocation id, int priority, Factory factory){}
    private static final List<Entry> ENTRIES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private ScriptPeripheralRegistry(){}
    public static synchronized void register(ResourceLocation id, int priority, Factory factory){
        Objects.requireNonNull(id); Objects.requireNonNull(factory);
        if(ENTRIES.stream().anyMatch(entry -> entry.id.equals(id))) throw new IllegalArgumentException("Duplicate script peripheral provider: " + id);
        ENTRIES.add(new Entry(id, priority, factory)); ENTRIES.sort(Comparator.comparingInt(Entry::priority).reversed());
    }
    public static ScriptPeripheralBinding resolve(BlockEntity target, Direction side){
        for(Entry entry : ENTRIES){ ScriptPeripheralBinding binding = entry.factory.resolve(target, side); if(binding != null) return binding; }
        return null;
    }
}
