package com.rieno.gadgetsandgizmos.lib.control;

import java.util.function.IntSupplier;

// Distinguish an input-only virtual signal from the physical power a receiver emits
public final class ReceiverSignalScope{
    private static final ThreadLocal<Boolean> RECEIVING = ThreadLocal.withInitial(() -> true);

    private ReceiverSignalScope(){}

    public static boolean allowsReceiverSignals(){
        return RECEIVING.get();
    }

    // Nested source queries retain their caller's scope, including after an exception
    public static int sampleSources(IntSupplier sample){
        if(!RECEIVING.get()) return sample.getAsInt();
        RECEIVING.set(false);
        try{
            return sample.getAsInt();
        }finally{
            RECEIVING.set(true);
        }
    }
}
