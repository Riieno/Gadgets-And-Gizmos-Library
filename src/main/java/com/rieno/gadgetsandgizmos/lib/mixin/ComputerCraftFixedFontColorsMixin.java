package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.ComputerCraftTerminalRenderer;
import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Render direct terminal cells in native computer screens and pocket computers
@Mixin(value = FixedWidthFontRenderer.class, remap = false)
public abstract class ComputerCraftFixedFontColorsMixin{
    @Shadow private static void quad(FixedWidthFontRenderer.QuadEmitter out, float x1, float y1, float x2, float y2,
            float z, int color, float u1, float v1, float u2, float v2, int light){ throw new AssertionError(); }

    private static ComputerCraftTerminalRenderer.Quad emitter(FixedWidthFontRenderer.QuadEmitter out){
        return (x1, y1, x2, y2, color, u1, v1, u2, v2) -> quad(out, x1, y1, x2, y2, 0, color, u1, v1, u2, v2, 0xF000F0);
    }
    @Inject(method = "drawTerminalForeground", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$foreground(FixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        ComputerCraftTerminalRenderer.foreground(terminal, x, y, emitter(out)); ci.cancel();
    }
    @Inject(method = "drawTerminalBackground", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$background(FixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal,
            float top, float bottom, float left, float right, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        ComputerCraftTerminalRenderer.background(terminal, x, y, top, bottom, left, right, emitter(out)); ci.cancel();
    }
    @Inject(method = "drawCursor", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$cursor(FixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        if(FrameInfo.getGlobalCursorBlink()) ComputerCraftTerminalRenderer.cursor(terminal, x, y, emitter(out));
        ci.cancel();
    }
}
