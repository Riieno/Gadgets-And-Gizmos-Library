package com.rieno.gadgetsandgizmos.lib.graph;

// Let a graph host disable command execution without removing saved node definitions
@FunctionalInterface
public interface CommandExecutionPolicy{
    // Read the host's current authoritative setting for each execution or output read
    boolean isEnabled();
}
