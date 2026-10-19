package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;

// Keep world render callbacks inside the active capture's framebuffer and viewport
@Mixin(value = ClientHooks.class, remap = false)
public abstract class ViewSceneRenderStageMixin{
    // Restore the scene after callbacks clear or draw into their own full-size targets
    @WrapMethod(method = "dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;"
            + "Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/culling/Frustum;)V")
    private static void preserveCaptureTarget(RenderLevelStageEvent.Stage stage, LevelRenderer renderer,
            PoseStack stack, Matrix4f view, Matrix4f projection, int tick, Camera camera, Frustum frustum,
            Operation<Void> original){
        if(!ViewSceneRenderer.isCapturing()){
            original.call(stage, renderer, stack, view, projection, tick, camera, frustum);
            return;
        }
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try{
            original.call(stage, renderer, stack, view, projection, tick, camera, frustum);
        }finally{
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
