package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.physics.ColoredLightBridge;
import dev.engine_room.flywheel.api.backend.BackendManager;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.world.phys.Vec3;

// Supply optional deferred point lights when the active backend supports Veil scene buffers
public final class VeilPointLightBackend implements ColoredLightBridge.Light {
    private LightRenderer renderer;
    private LightRenderHandle<PointLightData> handle;

    private VeilPointLightBackend() {
    }

    // Register the optional renderer
    public static void install() {
        ColoredLightBridge.install(VeilPointLightBackend::new);
    }

    // Move and recolor one light with its emitter
    @Override
    public void update(Vec3 position, int color, float radius, float brightness) {
        // Flywheel shaders do not populate Veil's deferred attachments; callers retain vanilla light.
        VeilRenderer veil = VeilRenderSystem.renderer();
        if (veil == null || position == null || BackendManager.isBackendOn()) {
            close();
            return;
        }
        LightRenderer next = veil.getLightRenderer();
        if (next != renderer || handle != null && !handle.isValid()) close();
        renderer = next;
        if (renderer == null) return;
        if (handle == null) {
            PointLightData data = new PointLightData().setPosition(position.x, position.y, position.z)
                    .setColor(color).setRadius(radius).setBrightness(brightness);
            handle = renderer.addLight(data);
            return;
        }
        handle.getLightData().setPosition(position.x, position.y, position.z)
                .setColor(color).setRadius(radius).setBrightness(brightness);
        handle.markDirty();
    }

    // Release the renderer-owned light
    @Override
    public void close() {
        if (handle != null && handle.isValid()) handle.free();
        handle = null;
        renderer = null;
    }
}
