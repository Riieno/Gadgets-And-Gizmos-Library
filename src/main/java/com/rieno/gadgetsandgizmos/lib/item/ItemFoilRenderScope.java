package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

// Scope foil overrides to the rendered item and the current render thread
public final class ItemFoilRenderScope {
    private static final ThreadLocal<Map<Item, Boolean>> OVERRIDES = new ThreadLocal<>();

    private ItemFoilRenderScope() {}

    public static @Nullable Boolean overrideFor(Item item) {
        Map<Item, Boolean> overrides = OVERRIDES.get();
        return overrides == null ? null : overrides.get(item);
    }

    public static void withoutFoil(Item item, Runnable render) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(render, "render");
        Map<Item, Boolean> overrides = OVERRIDES.get();
        if(overrides == null){
            overrides = new IdentityHashMap<>();
            OVERRIDES.set(overrides);
        }
        Boolean prev = overrides.put(item, false);
        try{
            render.run();
        }finally{
            if(prev == null) overrides.remove(item);
            else overrides.put(item, prev);
            if(overrides.isEmpty()) OVERRIDES.remove();
        }
    }
}
