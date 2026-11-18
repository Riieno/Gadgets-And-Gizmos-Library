package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import com.rieno.gadgetsandgizmos.lib.script.TerminalColors;

// Expose direct terminal cells without exposing a mixin implementation
public interface ComputerCraftTerminalAccess{
    TerminalColors terminalColors();
}
