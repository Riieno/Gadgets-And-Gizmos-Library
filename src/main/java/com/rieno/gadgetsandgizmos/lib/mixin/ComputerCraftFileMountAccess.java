package com.rieno.gadgetsandgizmos.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.nio.file.Path;

// Resolve physical CC volumes for a local host's file explorer
@Mixin(targets = "dan200.computercraft.core.filesystem.FileMount", remap = false)
public interface ComputerCraftFileMountAccess{
    @Accessor("root") Path gadgetsngizmos$root();
}
