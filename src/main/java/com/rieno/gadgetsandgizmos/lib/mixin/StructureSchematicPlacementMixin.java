package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicPlacementScope;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;

// Mark vanilla structure and compatible schematic placement callbacks as copies
@Mixin(StructureTemplate.class)
public abstract class StructureSchematicPlacementMixin{
    // Release placement context after success, rejection or an exception
    @WrapMethod(method = "placeInWorld")
    private boolean gadgetsngizmos$placeCopy(ServerLevelAccessor level, BlockPos pos, BlockPos pivot,
            StructurePlaceSettings settings, RandomSource random, int flags, Operation<Boolean> original){
        try(var scope = SchematicPlacementScope.enter()){ return original.call(level, pos, pivot, settings, random, flags); }
    }
}
