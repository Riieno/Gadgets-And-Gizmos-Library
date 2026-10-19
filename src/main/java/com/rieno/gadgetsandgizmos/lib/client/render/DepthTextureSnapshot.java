package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

// Copy scene depth into a sampleable texture without requiring matching framebuffer formats
public final class DepthTextureSnapshot implements AutoCloseable{
    private int texture;
    private int width;
    private int height;

    // Copy a rectangle from the current draw target's depth on its render thread
    public int capture(int x, int y, int width, int height){
        RenderSystem.assertOnRenderThreadOrInit();
        if(width <= 0 || height <= 0) return 0;
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            if(texture == 0) texture = GlStateManager._genTexture();
            GlStateManager._bindTexture(texture);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            if(this.width != width || this.height != height){
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                        x, y, width, height, 0);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                this.width = width;
                this.height = height;
            }else{
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
            }
            return texture;
        }finally{
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GlStateManager._bindTexture(prevTexture);
        }
    }

    // Release GPU storage on the owning render thread and allow a later capture to allocate it again
    @Override
    public void close(){
        RenderSystem.assertOnRenderThreadOrInit();
        if(texture != 0) GlStateManager._deleteTexture(texture);
        texture = 0;
        width = 0;
        height = 0;
    }
}
