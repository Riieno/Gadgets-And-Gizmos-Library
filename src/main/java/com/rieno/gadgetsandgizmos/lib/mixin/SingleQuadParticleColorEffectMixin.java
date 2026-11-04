package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectParticleRendering;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;

// Supply independent tint attributes only for particles carrying a synchronized preset
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleColorEffectMixin extends Particle{
    protected SingleQuadParticleColorEffectMixin(ClientLevel level, double x, double y, double z){ super(level, x, y, z); }

    @WrapMethod(method = "render")
    private void gadgetsngizmos$colorEffect(VertexConsumer buffer, Camera camera, float partialTick, Operation<Void> original){
        original.call(ColorEffectParticleRendering.wrap(this, buffer, age, lifetime, partialTick), camera, partialTick);
    }
}
