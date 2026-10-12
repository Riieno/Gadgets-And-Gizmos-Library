package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.TimedItemTransformations;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleContainer.class)
public abstract class TimedSimpleContainerMixin{
    @Inject(method = "getItem", at = @At("RETURN"))
    private void gadgetsngizmos$observeRead(int slot, CallbackInfoReturnable<ItemStack> cir){
        TimedItemTransformations.observe((SimpleContainer)(Object)this, cir.getReturnValue());
    }

    @Inject(method = "setItem", at = @At("RETURN"))
    private void gadgetsngizmos$observeWrite(int slot, ItemStack stack, CallbackInfo ci){
        SimpleContainer self = (SimpleContainer)(Object)this;
        TimedItemTransformations.observe(self, self.getItem(slot));
    }
}
