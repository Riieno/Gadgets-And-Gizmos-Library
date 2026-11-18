package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalColors;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Add the extended blit method to computers and every native TermMethods monitor
@Mixin(value = TermMethods.class, remap = false)
public abstract class ComputerCraftTerminalMethodsMixin{
    @Shadow public abstract Terminal getTerminal() throws LuaException;
    @LuaFunction
    public final void blitHex(IArguments args) throws LuaException{ ComputerCraftTerminalColors.blit(getTerminal(), args); }
}
