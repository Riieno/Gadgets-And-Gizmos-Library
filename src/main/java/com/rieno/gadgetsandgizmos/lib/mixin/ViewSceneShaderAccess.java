package com.rieno.gadgetsandgizmos.lib.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Keep the immediate shader cache consistent with the restored GPU program
@Mixin(ShaderInstance.class)
public interface ViewSceneShaderAccess{
    @Accessor("lastProgramId")
    static int gadgetsngizmos$getProgram(){ throw new AssertionError(); }

    @Accessor("lastProgramId")
    static void gadgetsngizmos$setProgram(int val){ throw new AssertionError(); }

    @Accessor("lastAppliedShader")
    static ShaderInstance gadgetsngizmos$getShader(){ throw new AssertionError(); }

    @Accessor("lastAppliedShader")
    static void gadgetsngizmos$setShader(ShaderInstance val){ throw new AssertionError(); }
}
