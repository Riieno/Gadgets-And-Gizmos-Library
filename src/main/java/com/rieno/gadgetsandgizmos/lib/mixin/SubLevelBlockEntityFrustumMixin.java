package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.geometry.ClipBounds;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Apply vanilla's individual renderer bounds to moving ship block entities
@Mixin(VanillaSubLevelBlockEntityRenderer.class)
public abstract class SubLevelBlockEntityFrustumMixin{
    @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    @Unique private final Matrix4f gadgetsngizmos$clip = new Matrix4f();

    // Keep offscreen renderers and partially visible bounds while rejecting fully clipped models
    @Inject(method = "renderSingleBE", at = @At("HEAD"), cancellable = true, remap = false)
    private void clipBlockEntity(BlockEntity be, PoseStack poses, float partialTick,
                                 double cameraX, double cameraY, double cameraZ, CallbackInfo ci){
        BlockEntityRenderer<BlockEntity> renderer = blockEntityRenderDispatcher.getRenderer(be);
        if(renderer == null || renderer.shouldRenderOffScreen(be)) return;
        var bounds = renderer.getRenderBoundingBox(be);
        gadgetsngizmos$clip.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                .mul(poses.last().pose());
        if(!ClipBounds.intersects(gadgetsngizmos$clip,
                bounds.minX - cameraX, bounds.minY - cameraY, bounds.minZ - cameraZ,
                bounds.maxX - cameraX, bounds.maxY - cameraY, bounds.maxZ - cameraZ)) ci.cancel();
    }
}
