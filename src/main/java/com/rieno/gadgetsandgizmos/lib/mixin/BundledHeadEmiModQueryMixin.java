package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// Use the same creator when EMI checks a stack outside its baked index
@Pseudo
@Mixin(targets = "dev.emi.emi.search.ModQuery", remap = false)
public abstract class BundledHeadEmiModQueryMixin{
    // Match unbaked packaged heads with the same mod-name filters as indexed entries
    @ModifyExpressionValue(method = "matchesUnbaked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;getNamespace()Ljava/lang/String;"), remap = false)
    private String gadgetsngizmos$headQueryOwner(String namespace, @Local(argsOnly = true) EmiStack stack){
        String owner = BundledSupporterHeadStack.creatorModId(stack.getItemStack());
        return owner == null ? namespace : owner;
    }
}
