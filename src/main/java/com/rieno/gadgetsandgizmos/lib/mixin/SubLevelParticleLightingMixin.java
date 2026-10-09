package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelEntityLighting;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Bound particle lighting before Sable scans an unfinished construction plot
@Mixin(value = Particle.class, priority = 900)
public abstract class SubLevelParticleLightingMixin{
    @Shadow @Final protected ClientLevel level;
    @Shadow public double x;
    @Shadow public double y;
    @Shadow public double z;

    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void particleLight(float partialTick, CallbackInfoReturnable<Integer> cir){
        BlockPos pos = BlockPos.containing(x, y, z);
        if(!level.hasChunkAt(pos)){ cir.setReturnValue(0); return; }
        if(level.getBlockState(pos).emissiveRendering(level, pos)){ cir.setReturnValue(LightTexture.FULL_BRIGHT); return; }
        int packed = LevelRenderer.getLightColor(level, pos);
        var tracking = ((ParticleExtension) this).sable$getTrackingSubLevel();
        var bodies = tracking == null ? Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(pos).expand(0.5)) : List.of(tracking);
        cir.setReturnValue(SubLevelEntityLighting.particleLight(packed, new Vector3d(x, y, z), bodies));
    }
}
