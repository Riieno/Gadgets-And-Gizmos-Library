package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelWeatherHeight;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bound both rain rendering and splash probes before Sable scans an unfinished plot
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class SubLevelWeatherHeightMixin{
    @Dynamic("Added by Sable's rain rendering mixin")
    @Inject(method = "sable$getSubLevelHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rainHeight(Level level, int x, int offset, int z, CallbackInfoReturnable<Integer> cir){
        var bounds = new BoundingBox3d(x, level.getMinBuildHeight(), z, (double) x + 1, level.getMaxBuildHeight(), (double) z + 1);
        cir.setReturnValue(SubLevelWeatherHeight.rainHeight(level, x, offset, z, Sable.HELPER.getAllIntersecting(level, bounds)));
    }
}
