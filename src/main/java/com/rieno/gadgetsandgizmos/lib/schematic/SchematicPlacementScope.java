package com.rieno.gadgetsandgizmos.lib.schematic;

// Distinguish copying a schematic from restoring the same saved block or dropped item
public final class SchematicPlacementScope implements AutoCloseable{
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    // Retain nested placement scopes on their owning game thread
    private final int prev;
    private boolean closed;

    // Retain an enclosing placement until this copy finishes
    private SchematicPlacementScope(){ prev = DEPTH.get(); DEPTH.set(prev + 1); }

    // Mark a synchronous schematic placement without changing its saved data
    public static SchematicPlacementScope enter(){ return new SchematicPlacementScope(); }

    // Identify copy-only loading callbacks while leaving ordinary world loads untouched
    public static boolean isActive(){ return DEPTH.get() > 0; }

    // Restore the enclosing operation even when block placement throws
    @Override
    public void close(){
        if(closed) return;
        closed = true;
        if(prev == 0) DEPTH.remove();
        else DEPTH.set(prev);
    }
}
