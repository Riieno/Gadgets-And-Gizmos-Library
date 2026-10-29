package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicPlacementScope;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

// Mark Create cannon and incremental construction callbacks as schematic copies
@Mixin(BlockHelper.class)
public abstract class CreateSchematicPlacementMixin{
    // Retain copy semantics while Create applies both item components and block entity data
    @WrapMethod(method = "placeSchematicBlock")
    private static void gadgetsngizmos$placeCopy(Level level, BlockState state, BlockPos pos, ItemStack stack,
            CompoundTag data, Operation<Void> original){
        try(var scope = SchematicPlacementScope.enter()){ original.call(level, state, pos, stack, data); }
    }
}
