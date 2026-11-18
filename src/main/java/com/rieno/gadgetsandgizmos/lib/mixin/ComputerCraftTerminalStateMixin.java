package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalState;
import com.rieno.gadgetsandgizmos.lib.script.TerminalColors;
import dan200.computercraft.shared.computer.terminal.TerminalState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

// Synchronize direct colors with the exact CC terminal update that owns their characters
@Mixin(value = TerminalState.class, remap = false)
public abstract class ComputerCraftTerminalStateMixin implements ComputerCraftTerminalState{
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final @Mutable private byte[] contents;

    // Keep palette-only packets byte compatible with CC's original terminal codec
    @Override public byte[] terminalColors(){
        int start = width * height * 2 + 48;
        if(contents.length < start || contents.length - start > TerminalColors.MAX_BYTES) throw new IllegalArgumentException("Invalid terminal color data");
        return Arrays.copyOfRange(contents, start, contents.length);
    }
    @Override public void terminalColors(byte[] data){
        if(data.length > TerminalColors.MAX_BYTES) throw new IllegalArgumentException("Invalid terminal color data");
        int start = width * height * 2 + 48;
        contents = Arrays.copyOf(contents, start + data.length);
        System.arraycopy(data, 0, contents, start, data.length);
    }
    @Override public byte[] nativeContents(){ return Arrays.copyOf(contents, width * height * 2 + 48); }
}
