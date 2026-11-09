package com.rieno.gadgetsandgizmos.lib.compat.emi;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.Objects;

// Expose packaged player heads independently of EMI's selected index source
public final class BundledHeadEmiSupport{
    // Prevent instances of the shared EMI adapter
    private BundledHeadEmiSupport(){}

    // Register texture identity and add only variants missing from the creative or registry index
    public static void register(EmiRegistry registry, Collection<ItemStack> variants){
        registry.setDefaultComparison(Items.PLAYER_HEAD, BundledHeadEmiSupport::withFallback);
        for(ItemStack stack : variants){
            if(BundledSupporterHeadStack.texture(stack) == null) continue;
            EmiStack variant = EmiStack.of(stack.copy());
            boolean exists = EmiApi.getIndexStacks().stream()
                    .anyMatch(existing -> existing.isEqual(variant, Comparison.compareComponents()));
            if(!exists) registry.addEmiStack(variant);
        }
    }

    // Retain the caller's comparison for heads without a packaged texture
    public static Comparison withFallback(Comparison prev){
        Objects.requireNonNull(prev, "prev");
        return Comparison.of((a, b) -> {
            var first = BundledSupporterHeadStack.texture(a.getItemStack());
            var second = BundledSupporterHeadStack.texture(b.getItemStack());
            return first == null && second == null ? prev.compare(a, b) : Objects.equals(first, second);
        }, stack -> Objects.hashCode(BundledSupporterHeadStack.texture(stack.getItemStack())));
    }
}
