package com.rieno.gadgetsandgizmos.lib.mixin;

import dan200.computercraft.core.computer.Computer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Read the live filesystem through CC's own synchronized storage API
@Mixin(targets = "dan200.computercraft.shared.computer.core.ServerComputer", remap = false)
public interface ComputerCraftStorageAccess{
    @Accessor("computer") Computer gadgetsngizmos$computer();
}
