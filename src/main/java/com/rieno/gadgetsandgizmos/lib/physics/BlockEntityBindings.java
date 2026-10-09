package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

// Associate real blocks with a controller without changing their world identity or lifetime
public final class BlockEntityBindings{
    private static final Map<BlockEntity, List<BlockEntity>> BINDINGS = new IdentityHashMap<>();

    private BlockEntityBindings(){}

    // Replace one controller's bindings only after every component passes ownership checks
    public static synchronized void replace(BlockEntity host, Collection<? extends BlockEntity> components){
        Objects.requireNonNull(host);
        List<BlockEntity> next = List.copyOf(components);
        var unique = java.util.Collections.newSetFromMap(new IdentityHashMap<BlockEntity, Boolean>());
        for(BlockEntity component : next){
            if(component == host || component.isRemoved() || component.getLevel() != host.getLevel()
                    || !unique.add(component)) throw new IllegalArgumentException("Invalid physical block binding");
            BlockEntity owner = host(component);
            if(owner != null && owner != host) throw new IllegalStateException("Another controller owns this block binding");
        }
        if(next.isEmpty()) BINDINGS.remove(host);
        else BINDINGS.put(host, next);
    }

    // Find a live controller without replacing normal block lookups
    public static synchronized @Nullable BlockEntity host(BlockEntity component){
        if(component == null || component.isRemoved()) return null;
        for(var entry : BINDINGS.entrySet()){
            if(!entry.getKey().isRemoved() && entry.getValue().stream().anyMatch(val -> val == component)) return entry.getKey();
        }
        return null;
    }

    // Retain a valid match or select the nearest available component from the caller's sublevel roster
    public static synchronized @Nullable BlockEntity select(BlockEntity host,
            Collection<? extends BlockEntity> components, @Nullable BlockEntity current,
            Collection<? extends BlockEntity> reserved, Predicate<BlockEntity> matches){
        Objects.requireNonNull(host);
        return components.stream().filter(component -> component != host && !component.isRemoved()
                && component.getLevel() == host.getLevel() && matches.test(component)
                && reserved.stream().noneMatch(val -> val == component)
                && (host(component) == null || host(component) == host))
                .min(java.util.Comparator.<BlockEntity>comparingDouble(component -> component == current ? -1
                        : component.getBlockPos().distSqr(host.getBlockPos()))
                        .thenComparing(BlockEntity::getBlockPos)).orElse(null);
    }

    // Release ownership without unloading or removing any physical component
    public static synchronized void remove(BlockEntity host){ BINDINGS.remove(host); }
}
