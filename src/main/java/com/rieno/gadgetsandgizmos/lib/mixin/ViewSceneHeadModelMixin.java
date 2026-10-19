package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Hide the lens owner's head only while its own feed is rendering
@Mixin(LivingEntityRenderer.class)
public abstract class ViewSceneHeadModelMixin{
    @Shadow protected EntityModel<LivingEntity> model;

    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void renderWithoutOwnHead(LivingEntity entity, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, Operation<Void> original){
        if(!ViewSceneRenderer.hidesHead(entity) || !(model instanceof HumanoidModel<?> humanoid)){
            original.call(entity, yaw, partialTick, poses, buffers, light);
            return;
        }
        boolean head = humanoid.head.visible, hat = humanoid.hat.visible;
        humanoid.head.visible = false;
        humanoid.hat.visible = false;
        try{ original.call(entity, yaw, partialTick, poses, buffers, light); }
        finally{ humanoid.head.visible = head;humanoid.hat.visible = hat; }
    }
}
