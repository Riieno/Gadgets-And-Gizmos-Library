package com.rieno.gadgetsandgizmos.lib.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Expose both halves for shared container permission checks
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccess{
    @Accessor("container1") Container gadgetsngizmos$first();
    @Accessor("container2") Container gadgetsngizmos$second();
}
