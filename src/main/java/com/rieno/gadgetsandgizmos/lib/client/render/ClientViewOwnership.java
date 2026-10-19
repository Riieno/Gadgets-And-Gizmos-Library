package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

// Yield player overlays, camera control and secondary renders to an external editor
public final class ClientViewOwnership{
    private static final Map<ResourceLocation, BooleanSupplier> OWNERS = new LinkedHashMap<>();
    private static volatile List<BooleanSupplier> snapshot = List.of();

    private ClientViewOwnership(){}

    // Register a live ownership check under the consuming mod's namespace
    public static synchronized void register(ResourceLocation id, BooleanSupplier active){
        Objects.requireNonNull(id);
        Objects.requireNonNull(active);
        if(OWNERS.putIfAbsent(id, active) != null) throw new IllegalArgumentException("Duplicate view owner " + id);
        snapshot = List.copyOf(OWNERS.values());
    }

    public static synchronized void unregister(ResourceLocation id){
        OWNERS.remove(id);
        snapshot = List.copyOf(OWNERS.values());
    }

    // Axiom can own the viewport without installing a Minecraft Screen
    public static boolean isReserved(){
        if(AxiomEditorCompat.isActive()) return true;
        for(BooleanSupplier active : snapshot){
            if(active.getAsBoolean()) return true;
        }
        return false;
    }
}
