package com.rieno.gadgetsandgizmos.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

// Resolve volume identity when the same disk is visible through several computers
@Mixin(targets = "dan200.computercraft.core.filesystem.FileSystem", remap = false)
public interface ComputerCraftFileSystemAccess{
    @Accessor("mounts") Map<String, ?> gadgetsngizmos$mounts();
}
