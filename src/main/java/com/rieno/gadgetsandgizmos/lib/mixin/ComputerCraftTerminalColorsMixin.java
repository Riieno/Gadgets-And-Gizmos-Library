package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalAccess;
import com.rieno.gadgetsandgizmos.lib.script.TerminalColors;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

// Keep native terminal mutations and direct colors under the terminal's existing lock
@Mixin(value = Terminal.class, remap = false)
public abstract class ComputerCraftTerminalColorsMixin implements ComputerCraftTerminalAccess{
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow protected int cursorX;
    @Shadow protected int cursorY;
    @Unique private TerminalColors gadgetsngizmos$colors;

    @Override public TerminalColors terminalColors(){
        if(gadgetsngizmos$colors == null) gadgetsngizmos$colors = new TerminalColors(width, height);
        return gadgetsngizmos$colors;
    }
    @Inject(method = "resize", at = @At("RETURN"))
    private void gadgetsngizmos$resize(int width, int height, CallbackInfo ci){ terminalColors().resize(width, height); }
    @Inject(method = "write", at = @At("HEAD"))
    private void gadgetsngizmos$write(String text, CallbackInfo ci){ terminalColors().write(cursorX, cursorY, text.length()); }
    @Inject(method = "blit", at = @At("HEAD"))
    private void gadgetsngizmos$blit(ByteBuffer text, ByteBuffer front, ByteBuffer back, CallbackInfo ci){
        terminalColors().paint(cursorX, cursorY, text.remaining(), TerminalColors.PALETTE, TerminalColors.PALETTE);
    }
    @Inject(method = "clear", at = @At("HEAD"))
    private void gadgetsngizmos$clear(CallbackInfo ci){ terminalColors().clear(); }
    @Inject(method = "clearLine", at = @At("HEAD"))
    private void gadgetsngizmos$clearLine(CallbackInfo ci){ terminalColors().clearLine(cursorY); }
    @Inject(method = "scroll", at = @At("HEAD"))
    private void gadgetsngizmos$scroll(int count, CallbackInfo ci){ terminalColors().scroll(count); }
    @Inject(method = "reset", at = @At("HEAD"))
    private void gadgetsngizmos$reset(CallbackInfo ci){ terminalColors().reset(); }
    @Inject(method = "setLine", at = @At("HEAD"))
    private void gadgetsngizmos$line(int row, String text, String front, String back, CallbackInfo ci){
        terminalColors().paint(0, row, Math.max(front.length(), back.length()), TerminalColors.PALETTE, TerminalColors.PALETTE);
    }
    @Inject(method = "setTextColour", at = @At("HEAD"))
    private void gadgetsngizmos$front(int color, CallbackInfo ci){
        var colors = terminalColors();
        if(colors.foreground() == TerminalColors.PALETTE) return;
        colors.foreground(TerminalColors.PALETTE);
        ((Terminal)(Object)this).setChanged();
    }
    @Inject(method = "setBackgroundColour", at = @At("HEAD"))
    private void gadgetsngizmos$back(int color, CallbackInfo ci){
        var colors = terminalColors();
        if(colors.background() == TerminalColors.PALETTE) return;
        colors.background(TerminalColors.PALETTE);
        ((Terminal)(Object)this).setChanged();
    }
}
