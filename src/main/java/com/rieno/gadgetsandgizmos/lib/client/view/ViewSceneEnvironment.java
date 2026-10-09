package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneFogAccess;

// Preserve atmosphere, GPU bindings and optional shader uniforms across a secondary scene
public final class ViewSceneEnvironment{
    // Prevent construction of the scoped state helper
    private ViewSceneEnvironment(){}

    // Snapshot water tint, render bindings and optional shader and LOD camera state
    public static Runnable save(){
        float red = ViewSceneFogAccess.gadgetsngizmos$getRed();
        float green = ViewSceneFogAccess.gadgetsngizmos$getGreen();
        float blue = ViewSceneFogAccess.gadgetsngizmos$getBlue();
        int target = ViewSceneFogAccess.gadgetsngizmos$getTarget();
        int prev = ViewSceneFogAccess.gadgetsngizmos$getPrevious();
        long changed = ViewSceneFogAccess.gadgetsngizmos$getChangedTime();
        float[] color = RenderSystem.getShaderFogColor().clone();
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        var shape = RenderSystem.getShaderFogShape();
        Runnable restoreGpu = ViewRenderState.save();
        Runnable restoreShaders = ViewShaderState.save();
        Runnable restoreLod = ViewLodCompat.save();
        return () -> {
            try{
                restoreLod.run();
            }finally{
                ViewSceneFogAccess.gadgetsngizmos$setRed(red);
                ViewSceneFogAccess.gadgetsngizmos$setGreen(green);
                ViewSceneFogAccess.gadgetsngizmos$setBlue(blue);
                ViewSceneFogAccess.gadgetsngizmos$setTarget(target);
                ViewSceneFogAccess.gadgetsngizmos$setPrevious(prev);
                ViewSceneFogAccess.gadgetsngizmos$setChangedTime(changed);
                RenderSystem.setShaderFogColor(color[0], color[1], color[2], color[3]);
                RenderSystem.setShaderFogStart(start);
                RenderSystem.setShaderFogEnd(end);
                RenderSystem.setShaderFogShape(shape);
                try{
                    restoreShaders.run();
                }finally{
                    ViewShaderState.restoreDrawState(restoreGpu);
                }
            }
        };
    }
}
