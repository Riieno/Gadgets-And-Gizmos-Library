package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Include passive inventories such as chests and barrels.
@Mixin(BlockEntity.class)
public abstract class TimedContainerBlockEntityMixin{
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void gadgetsngizmos$trackContainer(@Nullable Level level, CallbackInfo ci){
        if((Object)this instanceof Container container) TimedItemTransformations.track(container, level);
    }
}
