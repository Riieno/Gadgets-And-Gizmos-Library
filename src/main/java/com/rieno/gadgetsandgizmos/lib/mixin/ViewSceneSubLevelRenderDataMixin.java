package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Forward ship block changes to secondary meshes without sharing their culling state
@Mixin({FancySubLevelRenderData.class, VanillaChunkedSubLevelRenderData.class, VanillaSingleSubLevelRenderData.class})
public abstract class ViewSceneSubLevelRenderDataMixin{
    // Mark the corresponding section dirty in every feed that has rendered this ship
    @Inject(method = "setDirty", at = @At("HEAD"))
    private void updateViewSection(int x, int y, int z, boolean immediate, CallbackInfo ci){
        ViewSceneRenderer.updateSubLevelSection((SubLevelRenderData) (Object) this, x, y, z, immediate);
    }
}
