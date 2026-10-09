package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Map;

// Read host-owned node snapshots without exposing mutable block entity services
@FunctionalInterface
public interface GraphNodeOutputs{
    Map<String, GraphValue> snapshot(String nodeId);
}
