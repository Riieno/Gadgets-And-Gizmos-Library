package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

// Carry an immutable direct-color snapshot alongside CC's ordinary terminal state
public interface ComputerCraftTerminalState{
    byte[] terminalColors();
    void terminalColors(byte[] data);
    byte[] nativeContents();
}
