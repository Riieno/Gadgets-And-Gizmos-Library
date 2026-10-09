package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneAccess;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep secondary terrain storage and framebuffer state isolated from the player's view
@Mixin(LevelRenderer.class)
public abstract class ViewSceneLevelRendererMixin implements ViewSceneAccess{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Shadow private RenderTarget translucentTarget;
    @Shadow private RenderTarget itemEntityTarget;
    @Shadow private RenderTarget particlesTarget;
    @Shadow private RenderTarget weatherTarget;
    @Shadow private RenderTarget cloudsTarget;
    @Shadow private PostChain transparencyChain;
    @Shadow private Frustum cullingFrustum;
    @Shadow private double xTransparentOld;
    @Shadow private double yTransparentOld;
    @Shadow private double zTransparentOld;
    @Shadow private ViewArea viewArea;
    @Shadow private ClientLevel level;
    @Shadow private VertexBuffer starBuffer;
    @Shadow private VertexBuffer skyBuffer;
    @Shadow private VertexBuffer darkBuffer;
    @Shadow private VertexBuffer cloudBuffer;
    @Shadow public abstract void setLevel(ClientLevel level);
    @Shadow public abstract void close();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Use direct rendering until this scene restores its optional targets
    @Override
    public Runnable gadgetsngizmos$suspendViewTargets(){
        RenderTarget prevTranslucent = translucentTarget;
        RenderTarget prevItems = itemEntityTarget;
        RenderTarget prevParticles = particlesTarget;
        RenderTarget prevWeather = weatherTarget;
        RenderTarget prevClouds = cloudsTarget;
        PostChain prevTransparency = transparencyChain;
        Frustum prevFrustum = cullingFrustum;
        double prevX = xTransparentOld;
        double prevY = yTransparentOld;
        double prevZ = zTransparentOld;
        translucentTarget = itemEntityTarget = particlesTarget = weatherTarget = cloudsTarget = null;
        transparencyChain = null;
        return () -> {
            translucentTarget = prevTranslucent;
            itemEntityTarget = prevItems;
            particlesTarget = prevParticles;
            weatherTarget = prevWeather;
            cloudsTarget = prevClouds;
            transparencyChain = prevTransparency;
            cullingFrustum = prevFrustum;
            xTransparentOld = prevX;
            yTransparentOld = prevY;
            zTransparentOld = prevZ;
        };
    }
    // Reposition only this scene's chunk storage around its lens
    @Override
    public void gadgetsngizmos$prepareView(Vec3 pos){
        if(viewArea != null) viewArea.repositionCamera(pos.x, pos.z);
    }
    // Rebuild each feed after shader toggles, resource reloads and terrain refreshes
    @Inject(method = "allChanged", at = @At("TAIL"))
    private void reloadViewScenes(CallbackInfo ci){
        if(level != null) ViewSceneRenderer.invalidateScenes((LevelRenderer) (Object) this);
    }
    // Let the fog background fill the lower sky instead of drawing the underground void plane
    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 captureHorizonPosition(LocalPlayer player, float partialTick){
        var lens = ViewSceneRenderer.capturePose();
        if(lens == null) return player.getEyePosition(partialTick);
        return new Vec3(lens.position().x,
                Math.max(lens.position().y, level.getLevelData().getHorizonHeight(level) + 1), lens.position().z);
    }
    // Release all allocations when a feed expires or the client leaves its world
    @Override
    public void gadgetsngizmos$closeView(){
        setLevel(null);
        close();
        if(starBuffer != null) starBuffer.close();
        if(skyBuffer != null) skyBuffer.close();
        if(darkBuffer != null) darkBuffer.close();
        if(cloudBuffer != null) cloudBuffer.close();
    }
    // Leave the shared entity dispatcher attached to the player's client level
    @Redirect(method = "setLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;setLevel(Lnet/minecraft/world/level/Level;)V"))
    private void keepPlayerEntityLevel(EntityRenderDispatcher dispatcher, net.minecraft.world.level.Level level){
        if(!ViewSceneRenderer.ownsRenderer((LevelRenderer) (Object) this)) dispatcher.setLevel(level);
    }
    // Deliver block changes to each feed's independent terrain cache
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void updateViewSection(int x, int y, int z, boolean immediate, CallbackInfo ci){
        ViewSceneRenderer.updateSection((LevelRenderer) (Object) this, x, y, z);
    }
    // Make newly received chunks visible in every active camera scene
    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void updateViewChunk(ChunkPos pos, CallbackInfo ci){
        ViewSceneRenderer.updateChunk((LevelRenderer) (Object) this, pos);
    }
    // Omit decorative weather and cloud passes from small display feeds
    @Inject(method = {"renderClouds", "renderSnowAndRain"}, at = @At("HEAD"), cancellable = true)
    private void captureWithoutWeather(CallbackInfo ci){
        if(ViewSceneRenderer.isCapturing()) ci.cancel();
    }
    // Keep player outline post-processing attached to the main framebuffer
    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void captureWithoutOutlines(CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
