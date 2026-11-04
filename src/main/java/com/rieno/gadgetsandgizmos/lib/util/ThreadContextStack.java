package com.rieno.gadgetsandgizmos.lib.util;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

// Keep nested context overrides on the thread that owns them
public final class ThreadContextStack<T>{
    private final ThreadLocal<ArrayDeque<T>> values;

    public ThreadContextStack(Supplier<? extends T> initial){
        Objects.requireNonNull(initial);
        values = ThreadLocal.withInitial(() -> {
            var stack = new ArrayDeque<T>();
            stack.push(Objects.requireNonNull(initial.get()));
            return stack;
        });
    }

    // Read this thread's current override or its initial context
    public T current(){ return values.get().getFirst(); }

    // Retain an override until its owning operation finishes
    public void push(T val){ values.get().push(Objects.requireNonNull(val)); }

    // Restore the previous context without removing the initial value
    public void pop(){
        var stack = values.get();
        if(stack.size() == 1) throw new IllegalStateException("The initial context cannot be removed");
        stack.pop();
    }
}
