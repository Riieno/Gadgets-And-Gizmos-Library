package com.rieno.gadgetsandgizmos.lib.mixin;

import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.rieno.gadgetsandgizmos.lib.client.render.ParticleRenderOrdering;
import com.rieno.gadgetsandgizmos.lib.client.render.SoftParticleRenderTypes;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Order custom particle layers and resolve emissive batches
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineDepthResolveMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"))
    private Set<ParticleRenderType> gadgetsngizmos$orderLayers(Map<ParticleRenderType, Queue<Particle>> batches,
            Operation<Set<ParticleRenderType>> original) {
        return ParticleRenderOrdering.order(original.call(batches));
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void gadgetsngizmos$resolveDepth(MeshData mesh, Operation<Void> original,
            @Local ParticleRenderType type) {
        SoftParticleRenderTypes.drawEmissiveBatch(type, () -> original.call(mesh));
    }
}
