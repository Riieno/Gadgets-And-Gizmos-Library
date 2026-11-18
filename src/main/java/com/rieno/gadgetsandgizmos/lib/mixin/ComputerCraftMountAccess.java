package com.rieno.gadgetsandgizmos.lib.mixin;

import dan200.computercraft.api.filesystem.Mount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Identify a shared volume without exposing its internal wrapper
@Mixin(targets = "dan200.computercraft.core.filesystem.MountWrapper", remap = false)
public interface ComputerCraftMountAccess{
    @Accessor("mount") Mount gadgetsngizmos$mount();
    @Accessor("location") String gadgetsngizmos$location();
}
