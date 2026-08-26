package com.rieno.gadgetsandgizmos.lib.graph.export;

// Emit one registered graph node through a target-owned context
@FunctionalInterface
public interface GraphCodeEmitter<C> {
    // Emit the node through the target context
    void emit(C context);
}
