package com.rieno.gadgetsandgizmos.lib.interaction;

import org.jetbrains.annotations.Nullable;

// Carry interaction ownership through synchronous host writes without leaking between graphs
public final class InteractionContext{
    private static final ThreadLocal<InteractionOrigin> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Map<String, InteractionOrigin>> OUTPUTS = new ThreadLocal<>();
    private InteractionContext(){}
    public static @Nullable InteractionOrigin current(){ return CURRENT.get(); }
    public static void run(@Nullable InteractionOrigin origin, Runnable action){
        InteractionOrigin prev = CURRENT.get();
        if(origin == null) CURRENT.remove();
        else CURRENT.set(origin);
        try{ action.run(); }
        finally{
            if(prev == null) CURRENT.remove();
            else CURRENT.set(prev);
        }
    }
    // Carry each binding's latest writer through a batched host output commit
    public static void runOutputs(java.util.Map<String, InteractionOrigin> origins, Runnable action){
        var prev = OUTPUTS.get();
        OUTPUTS.set(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(origins)));
        try{ action.run(); }
        finally{ if(prev == null) OUTPUTS.remove(); else OUTPUTS.set(prev); }
    }
    // Apply the selected binding's origin without changing unrelated bindings in the same batch
    public static void runOutput(String binding, Runnable action){
        var origins = OUTPUTS.get();
        if(origins == null || !origins.containsKey(binding)){ action.run(); return; }
        run(origins.get(binding), action);
    }
}
