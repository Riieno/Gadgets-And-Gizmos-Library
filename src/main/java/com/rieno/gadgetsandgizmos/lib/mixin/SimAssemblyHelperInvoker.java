package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.List;

// Expose Simulated's assembly preparation without runtime reflection
@Mixin(value = SimAssemblyHelper.class, remap = false)
public interface SimAssemblyHelperInvoker {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prepare Create contraptions before a combined assembly
    @Invoker("disassembleAndAddCreateContraptions")
    static void ct$prepareCreateContraptions(Level level, BoundingBox3ic bounds,
                                             Collection<BlockPos> blocks, boolean moveEntities,
                                             List<AABB> glues) {
        throw new AssertionError();
    }
}
