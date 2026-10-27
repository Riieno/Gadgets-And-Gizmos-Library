package com.rieno.gadgetsandgizmos.lib.client.render;

// Mark miniature rendering so live screens can skip feed work without affecting the normal world pass
public final class HologramRenderScope implements AutoCloseable{
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private final int prev;
    private boolean closed;

    private HologramRenderScope(int prev){ this.prev = prev; }

    public static HologramRenderScope enter(){
        int prev = DEPTH.get();
        DEPTH.set(prev + 1);
        return new HologramRenderScope(prev);
    }

    public static boolean isActive(){ return DEPTH.get() > 0; }

    @Override public void close(){
        if(closed) return;
        closed = true;
        if(prev == 0) DEPTH.remove();
        else DEPTH.set(prev);
    }
}
