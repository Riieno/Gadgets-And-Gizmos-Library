package com.rieno.gadgetsandgizmos.lib.compat.jei;

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Keep packaged player-head textures distinct without changing ordinary player heads
public final class BundledHeadJeiSubtypeInterpreter implements ISubtypeInterpreter<ItemStack>{
    // Use the packaged texture as the stable variant identity
    @Override
    public @Nullable Object getSubtypeData(ItemStack stack, UidContext ctx){
        return BundledSupporterHeadStack.texture(stack);
    }

    // Preserve JEI's empty subtype for unmarked player heads
    @Override
    public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext ctx){
        var texture = BundledSupporterHeadStack.texture(stack);
        return texture == null ? "" : texture.toString();
    }
}
