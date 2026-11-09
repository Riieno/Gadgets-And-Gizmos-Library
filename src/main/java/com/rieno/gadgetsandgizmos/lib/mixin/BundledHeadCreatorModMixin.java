package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolve packaged skin ownership through NeoForge's per-stack creator contract
@Mixin(value = CommonHooks.class, remap = false)
public abstract class BundledHeadCreatorModMixin{
    // Leave ordinary items on NeoForge's normal creator lookup
    @Inject(method = "getDefaultCreatorModId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gadgetsngizmos$headCreator(ItemStack stack, CallbackInfoReturnable<String> cir){
        String owner = BundledSupporterHeadStack.creatorModId(stack);
        if(owner != null) cir.setReturnValue(owner);
    }
}
