package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Cool transported stacks even when the belt is stopped.
@Mixin(BeltInventory.class)
public abstract class TimedBeltInventoryMixin{
    @Shadow @Final private BeltBlockEntity belt;
    @Shadow @Final private List<TransportedItemStack> toInsert;
    @Shadow public abstract List<TransportedItemStack> getTransportedItems();

    @Inject(method = "tick", at = @At("HEAD"))
    private void gadgetsngizmos$transformItems(CallbackInfo ci){
        if(belt.getLevel() == null || belt.getLevel().isClientSide) return;
        boolean changed = gadgetsngizmos$transform(getTransportedItems());
        changed |= gadgetsngizmos$transform(toInsert);
        if(changed) belt.notifyUpdate();
    }

    private boolean gadgetsngizmos$transform(List<TransportedItemStack> items){
        boolean changed = false;
        for(TransportedItemStack transported : items){
            changed |= TimedItemTransformations.beginTransformation(transported.stack, belt.getLevel());
            Vec3 pos = BeltHelper.getVectorForOffset(belt, transported.beltPosition);
            AABB bounds = new AABB(pos.x - 0.125D, pos.y + 0.375D, pos.z - 0.125D,
                    pos.x + 0.125D, pos.y + 0.625D, pos.z + 0.125D);
            ItemStack converted = TimedItemTransformations.transform(transported.stack, belt.getLevel(), bounds);
            if(converted == transported.stack) continue;
            transported.stack = converted;
            transported.clearFanProcessingData();
            changed = true;
        }
        return changed;
    }
}
