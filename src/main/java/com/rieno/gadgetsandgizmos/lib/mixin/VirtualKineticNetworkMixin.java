package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticPos;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Keep virtual kinetic members stable inside Create kinetic networks
@Mixin(KineticNetwork.class)
public abstract class VirtualKineticNetworkMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the virtual network member
    @WrapOperation(method = {"calculateCapacity", "calculateStress"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity ct$resolveVirtualNetworkMember(Level level, BlockPos pos, Operation<BlockEntity> original) {
        BlockEntity blockEntity = original.call(level, pos);
        if (!(pos instanceof VirtualKineticPos virtualPos) || !(blockEntity instanceof VirtualKineticProvider provider)) {
            return blockEntity;
        }
        if (blockEntity instanceof KineticBlockEntity kineticBlockEntity
                && !GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor(kineticBlockEntity, pos)) {
            return blockEntity;
        }
        KineticBlockEntity virtual = provider.ct$getVirtualKinetic(virtualPos.ct$getVirtualSlot());
        return virtual != null ? virtual : blockEntity;
    }
}
