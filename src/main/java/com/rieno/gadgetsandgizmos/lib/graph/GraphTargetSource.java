package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.List;

// Supply the targets currently available to one graph host on its owning thread
@FunctionalInterface
public interface GraphTargetSource{
    List<GraphValue> targets();
}
