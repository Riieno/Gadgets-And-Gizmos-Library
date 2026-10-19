package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Omit helmet models and their custom armor hooks from the wearer's first-person feed
@Mixin(HumanoidArmorLayer.class)
public abstract class ViewSceneHeadEquipmentMixin{
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void omitOwnHelmet(PoseStack poses, MultiBufferSource buffers, LivingEntity entity,
            EquipmentSlot slot, int light, HumanoidModel<?> model, float swing, float amount,
            float partialTick, float age, float yaw, float pitch, CallbackInfo ci){
        if(slot == EquipmentSlot.HEAD && ViewSceneRenderer.hidesHead(entity)) ci.cancel();
    }
}
