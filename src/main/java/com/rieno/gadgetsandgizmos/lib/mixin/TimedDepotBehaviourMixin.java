package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DepotBehaviour.class)
public abstract class TimedDepotBehaviourMixin{
    @Shadow private TransportedItemStack heldItem;
    @Shadow private List<TransportedItemStack> incoming;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void gadgetsngizmos$transformItems(CallbackInfo ci){
        BlockEntityBehaviour self = (BlockEntityBehaviour)(Object)this;
        if(self.getWorld().isClientSide) return;
        boolean changed = gadgetsngizmos$transform(heldItem, self);
        for(TransportedItemStack transported : incoming){
            changed |= gadgetsngizmos$transform(transported, self);
        }
        if(changed) self.blockEntity.notifyUpdate();
    }

    private boolean gadgetsngizmos$transform(TransportedItemStack transported, BlockEntityBehaviour self){
        if(transported == null) return false;
        boolean starting = TimedItemTransformations.beginTransformation(transported.stack, self.getWorld());
        BlockPos pos = self.getPos();
        AABB bounds = new AABB(pos.getX() + 0.375D, pos.getY() + 0.875D, pos.getZ() + 0.375D,
                pos.getX() + 0.625D, pos.getY() + 1.125D, pos.getZ() + 0.625D);
        ItemStack converted = TimedItemTransformations.transform(transported.stack, self.getWorld(), bounds);
        if(converted == transported.stack) return starting;
        transported.stack = converted;
        transported.clearFanProcessingData();
        return true;
    }
}
