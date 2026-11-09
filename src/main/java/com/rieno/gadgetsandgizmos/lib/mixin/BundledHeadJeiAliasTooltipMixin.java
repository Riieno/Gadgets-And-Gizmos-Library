package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep packaged-head search metadata out of JEI's visible tooltip
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientGridTooltipHelper", remap = false)
public abstract class BundledHeadJeiAliasTooltipMixin{
    // Retain indexed aliases while skipping their heading and list for packaged heads
    @Inject(method = "addIngredientAliasesToTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void gadgetsngizmos$hideHeadAliases(@Coerce Object tooltip, ITypedIngredient<T> ingredient,
                                                  IIngredientManager manager, CallbackInfo ci){
        if(ingredient.getItemStack().map(BundledSupporterHeadStack::texture).isPresent()) ci.cancel();
    }
}
