package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneRenderSystemAccess;
import com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneShaderAccess;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

// Restore GPU bindings and Minecraft's corresponding caches after a secondary scene
final class ViewRenderState{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final GlStateBackup gl = new GlStateBackup();
    private final int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int cachedProgram = ViewSceneShaderAccess.gadgetsngizmos$getProgram();
    private final ShaderInstance appliedShader = ViewSceneShaderAccess.gadgetsngizmos$getShader();
    private final int array = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int activeTexture = GlStateManager._getActiveTexture();
    private final int[] textures = new int[12];
    private final int[] shaderTextures = ViewSceneRenderSystemAccess.gadgetsngizmos$textures().clone();
    private final int[] viewport = new int[4];
    private final int[] scissor = new int[4];
    private final float[] clearColor = new float[4];
    private final float[] shaderColor = RenderSystem.getShaderColor().clone();
    private final float lineWidth = RenderSystem.getShaderLineWidth();
    private final float glintAlpha = RenderSystem.getShaderGlintAlpha();
    private final int blendRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int blendAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
    private final int unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
    private final int unpackRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
    private final int unpackPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
    private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
    private final ShaderInstance shader = RenderSystem.getShader();
    private final Vector3f light0 = copyLight(0);
    private final Vector3f light1 = copyLight(1);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Read bindings outside the capture so allocation and resize are covered too
    private ViewRenderState(){
        RenderSystem.backupGlState(gl);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        // Vanilla secondary shaders bind their samplers through these texture units
        for(int idx = 0; idx < textures.length; idx++){
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + idx);
            textures[idx] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(activeTexture);
    }

    // Return a restoration action for capture and resource lifecycle scopes
    static Runnable save(){
        ViewRenderState state = new ViewRenderState();
        return state::restore;
    }

    // Restore through cached APIs before returning the exact framebuffer and vertex bindings
    private void restore(){
        RenderSystem.restoreGlState(gl);
        GL20.glBlendEquationSeparate(blendRgb, blendAlpha);
        GlStateManager._scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShaderGlintAlpha(glintAlpha);
        RenderSystem.setTextureMatrix(textureMatrix);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderLights(light0, light1);
        for(int idx = 0; idx < shaderTextures.length; idx++){
            int texture = retainedTexture(shaderTextures[idx]);
            if(RenderSystem.getShaderTexture(idx) != texture) RenderSystem.setShaderTexture(idx, texture);
        }
        for(int idx = 0; idx < textures.length; idx++){
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + idx);
            GlStateManager._bindTexture(retainedTexture(textures[idx]));
        }
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, unpackRows);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, unpackPixels);
        ViewSceneShaderAccess.gadgetsngizmos$setProgram(cachedProgram);
        ViewSceneShaderAccess.gadgetsngizmos$setShader(appliedShader);
        GlStateManager._glUseProgram(program);
        // Rebind on the next immediate draw instead of trusting the capture's VAO cache
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(array);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    // Resource cleanup may have retired a texture which was still bound before the scope
    private static int retainedTexture(int val){ return val > 0 && GL11.glIsTexture(val) ? val : 0; }
    // GUI item lighting can replace or mutate the shared direction vectors
    private static Vector3f copyLight(int idx){
        Vector3f val = ViewSceneRenderSystemAccess.gadgetsngizmos$lights()[idx];
        return val == null ? null : new Vector3f(val);
    }
}
