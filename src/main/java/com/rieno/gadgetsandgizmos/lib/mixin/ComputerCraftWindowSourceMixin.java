package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftWindowSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

// Install hex-aware windows before multishell creates its terminal redirects
@Mixin(targets = "dan200.computercraft.shared.computer.core.ResourceMount", remap = false)
public abstract class ComputerCraftWindowSourceMixin{
    @Shadow @Final private String namespace;
    @Shadow @Final private String subPath;

    @Inject(method = "getFileContents(Ljava/lang/String;Ldan200/computercraft/shared/computer/core/ResourceMount$FileEntry;)[B",
            at = @At("RETURN"), cancellable = true)
    private void gadgetsngizmos$windows(String path, @Coerce Object entry, CallbackInfoReturnable<byte[]> cir) throws IOException{
        if(!namespace.equals("computercraft") || !(subPath + "/" + path).equals("lua/rom/apis/window.lua")) return;
        cir.setReturnValue(ComputerCraftWindowSource.extend(cir.getReturnValue()));
    }
}
