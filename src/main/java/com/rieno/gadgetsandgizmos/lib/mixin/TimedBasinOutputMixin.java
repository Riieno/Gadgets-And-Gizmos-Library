package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Include basin outputs waiting for room on a belt or depot.
@Mixin(BasinBlockEntity.class)
public abstract class TimedBasinOutputMixin{
    @Shadow protected List<ItemStack> spoutputBuffer;
    @Shadow protected IFluidHandler fluidCapability;

    @Inject(method = "tick", at = @At("HEAD"))
    private void gadgetsngizmos$transformOutputs(CallbackInfo ci){
        BasinBlockEntity self = (BasinBlockEntity)(Object)this;
        if(self.getLevel() == null || self.getLevel().isClientSide) return;
        boolean changed = TimedItemTransformations.transformInFluids(self.getInputInventory(), fluidCapability);
        changed |= TimedItemTransformations.transformInFluids(self.getOutputInventory(), fluidCapability);
        for(int idx = 0; idx < spoutputBuffer.size(); idx++){
            ItemStack stack = spoutputBuffer.get(idx);
            changed |= TimedItemTransformations.beginTransformation(stack, self.getLevel());
            ItemStack converted = TimedItemTransformations.transformInFluids(stack, fluidCapability);
            if(converted == stack) converted = TimedItemTransformations.transform(stack, self.getLevel());
            if(converted == stack) continue;
            spoutputBuffer.set(idx, converted);
            changed = true;
        }
        if(changed) self.notifyUpdate();
    }
}
