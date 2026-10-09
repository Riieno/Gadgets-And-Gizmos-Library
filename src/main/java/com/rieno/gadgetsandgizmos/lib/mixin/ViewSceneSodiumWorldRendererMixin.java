package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewChunkCompat;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Give secondary Sodium renderers their own chunk event history
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class ViewSceneSodiumWorldRendererMixin{
    @Unique private ViewChunkCompat gadgetsngizmos$chunks;

    // Seed the private history after Sodium has populated its own terrain manager
    @Inject(method = "initRenderer", at = @At("TAIL"), require = 0, remap = false)
    private void initializeViewChunks(CallbackInfo ci){
        if(ViewSceneRenderer.ownsRenderer(Minecraft.getInstance().levelRenderer)){
            gadgetsngizmos$chunks = new ViewChunkCompat(this);
        }
    }
    // Leave the client level's consumable chunk queue for the player's renderer
    @Inject(method = "processChunkEvents", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void updateViewChunks(CallbackInfo ci){
        if(gadgetsngizmos$chunks == null) return;
        gadgetsngizmos$chunks.update();
        ci.cancel();
    }
    // Forward terrain changes after Sodium bypasses vanilla dirty-section methods
    @Inject(method = "scheduleRebuildForChunk", at = @At("HEAD"), require = 0, remap = false)
    private void updateViewSection(int x, int y, int z, boolean immediate, CallbackInfo ci){
        if(gadgetsngizmos$chunks != null) return;
        ViewSceneRenderer.updateSection(Minecraft.getInstance().levelRenderer, x, y, z);
    }
    // Forward chunk packets and neighboring block changes to each retained source
    @Inject(method = "scheduleRebuildForChunks", at = @At("HEAD"), require = 0, remap = false)
    private void updateViewSections(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                    boolean immediate, CallbackInfo ci){
        if(gadgetsngizmos$chunks != null) return;
        var renderer = Minecraft.getInstance().levelRenderer;
        for(int x = minX; x <= maxX; x++){
            for(int y = minY; y <= maxY; y++){
                for(int z = minZ; z <= maxZ; z++) ViewSceneRenderer.updateSection(renderer, x, y, z);
            }
        }
    }
    // Release references when Sodium disposes this scene's terrain manager
    @Inject(method = "unloadLevel", at = @At("TAIL"), require = 0, remap = false)
    private void releaseViewChunks(CallbackInfo ci){
        gadgetsngizmos$chunks = null;
    }
}
