package com.rieno.gadgetsandgizmos.lib.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

// Restore attachments after the outer transfer has created its bodies and moved every block
public final class SubLevelTransferScope{
    // Retain completion callbacks on the owning game thread
    private static final ThreadLocal<List<Runnable>> CALLBACKS = new ThreadLocal<>();

    // Keep transfer completion scoped to native assembly operations
    private SubLevelTransferScope(){}

    // Complete nested transfers together and discard callbacks when the transfer fails
    public static <T> T run(Supplier<T> transfer){
        Objects.requireNonNull(transfer, "transfer");
        if(CALLBACKS.get() != null) return transfer.get();
        List<Runnable> callbacks = new ArrayList<>();
        CALLBACKS.set(callbacks);
        T res;
        try{ res = transfer.get(); }
        finally{ CALLBACKS.remove(); }
        for(Runnable callback : callbacks) callback.run();
        return res;
    }

    // Defer a loaded attachment's restoration until its native transfer has finished
    public static void afterTransfer(Runnable callback){
        Objects.requireNonNull(callback, "callback");
        List<Runnable> callbacks = CALLBACKS.get();
        if(callbacks == null) callback.run();
        else callbacks.add(callback);
    }
}
