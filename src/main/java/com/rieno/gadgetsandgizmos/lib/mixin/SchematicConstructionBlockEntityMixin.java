package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelConstructionState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep schematic machinery idle until its entire native assembly is present
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class SchematicConstructionBlockEntityMixin{
    @Shadow @Final private BlockEntity blockEntity;

    // Delay block entity ticks for unfinished schematic bodies
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$delayMachineTicks(CallbackInfo ci){
        var level = blockEntity.getLevel();
        if(level == null || level.isClientSide) return;
        var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, blockEntity.getBlockPos());
        if(body instanceof ServerSubLevel serverBody && SubLevelConstructionState.isBuilding(serverBody)) ci.cancel();
    }
}
