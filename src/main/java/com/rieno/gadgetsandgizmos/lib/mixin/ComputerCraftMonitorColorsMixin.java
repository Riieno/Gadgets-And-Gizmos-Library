package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.ComputerCraftTerminalRenderer;
import dan200.computercraft.client.render.monitor.MonitorBlockEntityRenderer;
import dan200.computercraft.client.render.monitor.MonitorRenderState;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.peripheral.monitor.ClientMonitor;
import dan200.computercraft.shared.peripheral.monitor.MonitorRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Select CC's full-color vertex path when a monitor contains direct ARGB cells
@Mixin(value = MonitorBlockEntityRenderer.class, remap = false)
public abstract class ComputerCraftMonitorColorsMixin{
    @Redirect(method = "renderTerminal", at = @At(value = "INVOKE", target = "Ldan200/computercraft/client/render/monitor/MonitorBlockEntityRenderer;currentRenderer()Ldan200/computercraft/shared/peripheral/monitor/MonitorRenderer;"))
    private static MonitorRenderer gadgetsngizmos$renderer(Matrix4f matrix, ClientMonitor monitor, MonitorRenderState state,
            Terminal terminal, float xMargin, float yMargin){
        return ComputerCraftTerminalRenderer.active(terminal) ? MonitorRenderer.VBO : MonitorBlockEntityRenderer.currentRenderer();
    }
}
