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
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Add virtual kinetic neighbours, source resolution and persistence to Create block entities
@Mixin(KineticBlockEntity.class)
public abstract class VirtualKineticBlockEntityMixin extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current connected virtual kinetic slot
    @Unique
    private int ct$connectedVirtualKineticSlot = -1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the virtual kinetic block entity
    protected VirtualKineticBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the virtual validate
    @WrapOperation(method = "validateKinetics", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity ct$resolveVirtualValidate(Level level, BlockPos pos, Operation<BlockEntity> original) {
        if (!GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor((KineticBlockEntity) (Object) this, pos)) {
            return original.call(level, pos);
        }
        return ct$resolveVirtualBlockEntity(original.call(level, pos), pos, ct$connectedVirtualKineticSlot);
    }

    // Resolve the virtual set source
    @WrapOperation(method = "setSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity ct$resolveVirtualSetSource(Level level, BlockPos pos, Operation<BlockEntity> original) {
        if (!GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor((KineticBlockEntity) (Object) this, pos)) {
            ct$connectedVirtualKineticSlot = -1;
            return original.call(level, pos);
        }
        if (pos instanceof VirtualKineticPos virtualPos) {
            ct$connectedVirtualKineticSlot = virtualPos.ct$getVirtualSlot();
        } else {
            ct$connectedVirtualKineticSlot = -1;
        }
        return ct$resolveVirtualBlockEntity(original.call(level, pos), pos, ct$connectedVirtualKineticSlot);
    }

    // Clear the virtual source slot
    @Inject(method = "removeSource", at = @At("TAIL"), remap = false)
    private void ct$clearVirtualSourceSlot(CallbackInfo ci) {
        ct$connectedVirtualKineticSlot = -1;
    }

    // Propagate the virtual set level
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void ct$propagateVirtualSetLevel(Level level, CallbackInfo ci) {
        if (!((Object) this instanceof VirtualKineticProvider provider)) {
            return;
        }
        for (KineticBlockEntity virtual : provider.ct$getVirtualKinetics()) {
            virtual.setLevel(level);
        }
    }

    // Propagate the virtual block state
    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void ct$propagateVirtualBlockState(BlockState state, CallbackInfo ci) {
        if (!((Object) this instanceof VirtualKineticProvider provider)) {
            return;
        }
        for (KineticBlockEntity virtual : provider.ct$getVirtualKinetics()) {
            virtual.setBlockState(state);
        }
    }

    // Invalidate the virtual kinetics
    @Inject(method = "invalidate", at = @At("TAIL"), remap = false)
    private void ct$invalidateVirtualKinetics(CallbackInfo ci) {
        if (!((Object) this instanceof VirtualKineticProvider provider)) {
            return;
        }
        for (KineticBlockEntity virtual : provider.ct$getVirtualKinetics()) {
            virtual.invalidate();
        }
    }

    // Remove the virtual kinetics
    @Inject(method = "remove", at = @At("TAIL"), remap = false)
    private void ct$removeVirtualKinetics(CallbackInfo ci) {
        if (!((Object) this instanceof VirtualKineticProvider provider)) {
            return;
        }
        for (KineticBlockEntity virtual : provider.ct$getVirtualKinetics()) {
            virtual.remove();
        }
    }

    // Switch the virtual kinetics
    @Inject(method = "switchToBlockState", at = @At("TAIL"))
    private static void ct$switchVirtualKinetics(Level world, BlockPos pos, BlockState state, CallbackInfo ci,
                                                 @Local BlockEntity be) {
        if (!(be instanceof VirtualKineticProvider provider)) {
            return;
        }
        for (KineticBlockEntity virtual : provider.ct$getVirtualKinetics()) {
            if (!virtual.hasNetwork()) {
                continue;
            }
            virtual.getOrCreateNetwork().remove(virtual);
            virtual.detachKinetics();
            virtual.removeSource();
            if (virtual instanceof GeneratingKineticBlockEntity generating) {
                generating.reActivateSource = true;
            }
        }
    }

    // Write the virtual kinetics
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void ct$writeVirtualKinetics(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket,
                                         CallbackInfo ci) {
        if (!GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor((KineticBlockEntity) (Object) this,
                ((KineticBlockEntity) (Object) this).getBlockPos())) {
            return;
        }
        if ((Object) this instanceof VirtualKineticProvider provider) {
            for (int slot = 0; slot < provider.ct$getVirtualKineticCount(); slot++) {
                KineticBlockEntity virtual = provider.ct$getVirtualKinetic(slot);
                if (virtual == null) {
                    continue;
                }
                CompoundTag internalTag = new CompoundTag();
                if (clientPacket) {
                    virtual.writeClient(internalTag, registries);
                } else {
                    virtual.saveAdditional(internalTag, registries);
                }
                compound.put(provider.ct$getVirtualKineticSaveName(slot), internalTag);
            }
        }
        if (ct$connectedVirtualKineticSlot >= 0) {
            compound.putInt("CTVirtualKineticSourceSlot", ct$connectedVirtualKineticSlot);
        }
    }

    // Read the virtual kinetics
    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void ct$readVirtualKinetics(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket,
                                        CallbackInfo ci) {
        if (!GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor((KineticBlockEntity) (Object) this,
                ((KineticBlockEntity) (Object) this).getBlockPos())) {
            ct$connectedVirtualKineticSlot = -1;
            return;
        }
        if ((Object) this instanceof VirtualKineticProvider provider) {
            for (int slot = 0; slot < provider.ct$getVirtualKineticCount(); slot++) {
                KineticBlockEntity virtual = provider.ct$getVirtualKinetic(slot);
                if (virtual == null) {
                    continue;
                }
                CompoundTag internalTag = compound.getCompound(provider.ct$getVirtualKineticSaveName(slot));
                if (clientPacket) {
                    virtual.readClient(internalTag, registries);
                } else {
                    virtual.loadCustomOnly(internalTag, registries);
                }
            }
        }
        ct$connectedVirtualKineticSlot = compound.contains("CTVirtualKineticSourceSlot")
                ? compound.getInt("CTVirtualKineticSourceSlot")
                : -1;
    }

    // Resolve the virtual block entity
    @Unique
    private static BlockEntity ct$resolveVirtualBlockEntity(BlockEntity blockEntity, BlockPos pos, int fallbackSlot) {
        if (!(blockEntity instanceof VirtualKineticProvider provider)) {
            return blockEntity;
        }

        int slot = fallbackSlot;
        if (pos instanceof VirtualKineticPos virtualPos) {
            slot = virtualPos.ct$getVirtualSlot();
        }

        if (slot < 0) {
            return blockEntity;
        }

        KineticBlockEntity virtual = provider.ct$getVirtualKinetic(slot);
        return virtual != null ? virtual : blockEntity;
    }
}
