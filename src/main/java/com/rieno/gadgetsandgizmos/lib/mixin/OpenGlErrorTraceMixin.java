package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.platform.GlDebug;
import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

// Include the calling renderer once for each driver error without suppressing vanilla diagnostics
@Mixin(GlDebug.class)
public abstract class OpenGlErrorTraceMixin{
    @Unique private static final Logger gadgetsngizmos$logger = LogUtils.getLogger();
    @Unique private static final Set<String> gadgetsngizmos$errors = new HashSet<>();

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void gadgetsngizmos$traceError(int source, int type, int id, int severity,
            int length, long message, long userParam, CallbackInfo ci){
        if(type != KHRDebug.GL_DEBUG_TYPE_ERROR) return;
        String text = GLDebugMessageCallback.getMessage(length, message);
        synchronized(gadgetsngizmos$errors){
            if(gadgetsngizmos$errors.size() >= 8 || !gadgetsngizmos$errors.add(text)) return;
        }
        gadgetsngizmos$logger.warn("[GadgetsNGizmos][Render] First OpenGL error: {}", text,
                new Throwable("OpenGL error calling renderer"));
    }
}
