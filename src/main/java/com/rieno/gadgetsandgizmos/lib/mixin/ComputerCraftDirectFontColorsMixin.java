package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.ComputerCraftTerminalRenderer;
import com.rieno.gadgetsandgizmos.lib.compat.MixinCompatibility;
import dan200.computercraft.client.render.text.DirectFixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Render direct cells through CC's native monitor vertex format and shader integration
@Mixin(value = DirectFixedWidthFontRenderer.class, remap = false)
public abstract class ComputerCraftDirectFontColorsMixin{
    // CC 1.120 moved byte-order conversion from the emitter to its caller
    @Unique private static final boolean gadgetsngizmos$nativeColors = MixinCompatibility.hasMethod(
            "dan200.computercraft.client.render.text.DirectFixedWidthFontRenderer", "quad",
            "(Ldan200/computercraft/client/render/text/DirectFixedWidthFontRenderer$QuadEmitter;FFFFIFFFF)V");
    private static ComputerCraftTerminalRenderer.Quad emitter(DirectFixedWidthFontRenderer.QuadEmitter out){
        return (x1, y1, x2, y2, color, u1, v1, u2, v2) -> {
            if(gadgetsngizmos$nativeColors){
                color = net.minecraft.util.FastColor.ABGR32.fromArgb32(color);
                if(java.nio.ByteOrder.nativeOrder() != java.nio.ByteOrder.LITTLE_ENDIAN) color = Integer.reverseBytes(color);
            }
            out.quad(x1, y1, x2, y2, 0, color, u1, v1, u2, v2);
        };
    }
    @Inject(method = "drawTerminalForeground", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$foreground(DirectFixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        ComputerCraftTerminalRenderer.foreground(terminal, x, y, emitter(out)); ci.cancel();
    }
    @Inject(method = "drawTerminalBackground", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$background(DirectFixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal,
            float top, float bottom, float left, float right, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        ComputerCraftTerminalRenderer.background(terminal, x, y, top, bottom, left, right, emitter(out)); ci.cancel();
    }
    @Inject(method = "drawCursor", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$cursor(DirectFixedWidthFontRenderer.QuadEmitter out, float x, float y, Terminal terminal, CallbackInfo ci){
        if(!ComputerCraftTerminalRenderer.active(terminal)) return;
        ComputerCraftTerminalRenderer.cursor(terminal, x, y, emitter(out)); ci.cancel();
    }
}
