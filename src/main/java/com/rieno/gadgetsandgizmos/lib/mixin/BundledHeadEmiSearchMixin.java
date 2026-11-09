package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// Index packaged heads under their creator while retaining their actual item registry IDs
@Pseudo
@Mixin(targets = "dev.emi.emi.search.EmiSearch", remap = false)
public abstract class BundledHeadEmiSearchMixin{
    // Supply the effective owner to both the mod-name and mod-ID search fields
    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;getNamespace()Ljava/lang/String;"), remap = false)
    private static String gadgetsngizmos$headSearchOwner(String namespace, @Local EmiStack stack){
        String owner = BundledSupporterHeadStack.creatorModId(stack.getItemStack());
        return owner == null ? namespace : owner;
    }
}
