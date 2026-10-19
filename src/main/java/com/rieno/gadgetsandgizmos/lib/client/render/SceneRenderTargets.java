package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import foundry.veil.api.client.render.texture.TextureFilter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

// Match Minecraft depth settings when other renderers copy between primary and secondary scenes
public final class SceneRenderTargets{
    private SceneRenderTargets(){}

    // Create an owned target with the source's standard Minecraft depth and stencil settings
    public static TextureTarget createLike(RenderTarget source, int width, int height, boolean clearError){
        RenderSystem.assertOnRenderThreadOrInit();
        TextureTarget target = new TextureTarget(width, height, source.useDepth, clearError);
        if(source.useDepth && source.isStencilEnabled()) target.enableStencil();
        return target;
    }

    // Retain smooth scene gradients before the final display draw quantizes them
    public static TextureTarget createPreciseLike(RenderTarget source, int width, int height, boolean clearError){
        RenderSystem.assertOnRenderThreadOrInit();
        TextureTarget target = new PreciseTarget(width, height, source.useDepth, clearError);
        if(source.useDepth && source.isStencilEnabled()) target.enableStencil();
        return target;
    }

    // Compare settings without reading GPU state during each frame
    public static boolean hasMatchingDepth(RenderTarget first, RenderTarget second){
        return first.useDepth == second.useDepth
                && (!first.useDepth || first.isStencilEnabled() == second.isStencilEnabled());
    }

    // Share live attachments with Veil while leaving texture ownership with the source target
    public static AdvancedFbo wrap(RenderTarget target){
        RenderSystem.assertOnRenderThreadOrInit();
        var builder = AdvancedFbo.withSize(target.width, target.height)
                .addColorTextureWrapper(target.getColorTextureId());
        if(target.useDepth){
            if(target.isStencilEnabled()) builder.setDepthBuffer(new SharedDepthStencil(target));
            else builder.setDepthTextureWrapper(target.getDepthTextureId());
        }
        return builder.build(true);
    }

    // Preserve the color format and filtering when Minecraft resizes or enables stencil
    private static final class PreciseTarget extends TextureTarget{
        private PreciseTarget(int width, int height, boolean useDepth, boolean clearError){
            super(width, height, useDepth, clearError);
        }

        @Override
        public void createBuffers(int width, int height, boolean clearError){
            super.createBuffers(width, height, clearError);
            GlStateManager._bindTexture(getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height,
                    0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
            setFilterMode(GL11.GL_LINEAR);
            bindWrite(false);
            checkStatus();
            clear(clearError);
            unbindRead();
        }
    }

    // Expose both aspects of a borrowed depth-stencil texture without allocating another attachment
    private static final class SharedDepthStencil extends AdvancedFboTextureAttachment{
        private final int texture;

        private SharedDepthStencil(RenderTarget target){
            super(GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_DEPTH_STENCIL, GL30.GL_DEPTH32F_STENCIL8,
                    target.width, target.height, 0, TextureFilter.CLAMP, null);
            texture = target.getDepthTextureId();
        }

        // Return the attachment owned by the Minecraft target
        @Override
        public int getId(){ return texture; }

        // The source has already allocated this texture
        @Override
        public void create(){}

        // The source releases its texture after the framebuffer wrapper is freed
        @Override
        public void free(){}
    }
}
