package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewShaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep a camera mesh's GPU stride consistent with its compiled vertex data
@Mixin(VertexBuffer.class)
public abstract class ViewSceneVertexBufferMixin{
    @Unique private boolean gadgetsngizmos$cameraMesh;

    // Retain ownership when a queued camera upload completes during the player's frame
    @Inject(method = "<init>", at = @At("TAIL"))
    private void rememberViewMesh(CallbackInfo ci){
        gadgetsngizmos$cameraMesh = ViewSceneRenderer.isCapturing();
    }
    // Preserve the mesh's explicit format instead of expanding it during upload
    @WrapOperation(method = "uploadVertexBuffer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexFormat;setupBufferState()V"))
    private void uploadMeshFormat(VertexFormat format, Operation<Void> original){
        if(gadgetsngizmos$cameraMesh) ViewShaderCompat.withMeshFormat(() -> original.call(format));
        else original.call(format);
    }
}
