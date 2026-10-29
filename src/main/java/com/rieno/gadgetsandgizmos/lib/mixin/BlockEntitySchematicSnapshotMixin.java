package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotScope;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Let vanilla structure capture use the requested authoritative snapshots
@Mixin(BlockEntity.class)
public abstract class BlockEntitySchematicSnapshotMixin{
    @Inject(method = "saveWithId", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$schematicSnapshot(HolderLookup.Provider provider,
                                                 CallbackInfoReturnable<CompoundTag> cir){
        CompoundTag data = SchematicSnapshotScope.data((BlockEntity) (Object) this);
        if(data != null) cir.setReturnValue(data);
    }
}
