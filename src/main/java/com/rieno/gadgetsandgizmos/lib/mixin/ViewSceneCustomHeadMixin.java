package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep skulls and other non-armor head items out of their own camera feed
@Mixin(CustomHeadLayer.class)
public abstract class ViewSceneCustomHeadMixin{
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void omitOwnHeadItem(PoseStack poses, MultiBufferSource buffers, int light, LivingEntity entity,
            float swing, float amount, float partialTick, float age, float yaw, float pitch, CallbackInfo ci){
        if(ViewSceneRenderer.hidesHead(entity)) ci.cancel();
    }
}
