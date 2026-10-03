package com.rieno.gadgetsandgizmos.lib.mixin;

import java.util.function.Predicate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.render.ParticleRenderOrdering;
import com.rieno.gadgetsandgizmos.lib.client.render.SoftParticleRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Flush queued world geometry before early particle layers
@Mixin(LevelRenderer.class)
public abstract class LevelRendererParticleOrderMixin {
    @Shadow @Final private RenderBuffers renderBuffers;

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V"))
    private void gadgetsngizmos$deferParticles(ParticleEngine engine, LightTexture lightTexture,
            Camera camera, float partialTick, Frustum frustum, Predicate<ParticleRenderType> predicate,
            Operation<Void> original) {
        original.call(engine, lightTexture, camera, partialTick, frustum,
                (Predicate<ParticleRenderType>) type -> predicate.test(type)
                        && !ParticleRenderOrdering.rendersAfterClouds(type));
    }

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"))
    private void gadgetsngizmos$renderAfterClouds(DeltaTracker deltaTracker, boolean renderBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix,
            Matrix4f projectionMatrix, CallbackInfo ci, @Local Frustum frustum) {
        if (!ParticleRenderOrdering.hasAfterCloudsLayers()
                || SoftParticleRenderTypes.isShaderPackActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget particles = minecraft.levelRenderer.getParticlesTarget();
        RenderTarget weather = minecraft.levelRenderer.getWeatherTarget();
        int drawTarget = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        boolean separateTargets = particles != null && weather != null && Minecraft.useShaderTransparency()
                && drawTarget == weather.frameBufferId;
        if (separateTargets) particles.bindWrite(false);
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.particleEngine.render(lightTexture, camera,
                    deltaTracker.getGameTimeDeltaPartialTick(false), frustum,
                    ParticleRenderOrdering::rendersAfterClouds);
        } finally {
            if (separateTargets) weather.bindWrite(false);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.depthMask(depthWrite);
        }
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void gadgetsngizmos$flushBeforeEarlyParticles(MultiBufferSource.BufferSource source,
            Operation<Void> original) {
        original.call(source);
        if (source == this.renderBuffers.crumblingBufferSource()) {
            ParticleRenderOrdering.flushDeferredGeometry(this.renderBuffers);
        }
    }
}
