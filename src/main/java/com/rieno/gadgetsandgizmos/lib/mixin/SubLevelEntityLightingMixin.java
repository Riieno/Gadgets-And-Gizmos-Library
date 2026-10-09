package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelEntityLighting;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bound Sable's entity skylight scan after its renderer methods have been merged
@Mixin(value = EntityRenderer.class, priority = 900)
public abstract class SubLevelEntityLightingMixin{
    // Sample unfinished plots without scanning sentinel bounds or overflowing block coordinates
    @Dynamic("Added by Sable's entity rendering mixin")
    @Inject(method = "sable$getSubLevelAccountedSkyLight", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skyLight(int packed, Level level, LightLayer layer, BlockPos pos, Vector3dc worldProbe, CallbackInfoReturnable<Integer> cir){
        if(layer != LightLayer.SKY) return;
        int sky = packed == -1 ? level.getBrightness(layer, pos) : LightTexture.sky(packed);
        cir.setReturnValue(SubLevelEntityLighting.skyLight(sky, worldProbe,
                Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(pos))));
    }
}
