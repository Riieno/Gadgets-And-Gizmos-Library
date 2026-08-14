package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.HeldKineticAngleAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

// Add held-angle state and propagation to Create kinetic block entities
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityHeldAngleMixin implements HeldKineticAngleAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current held angles
    @Unique
    private float[] ct$heldAngles;

    // Current held angle active
    @Unique
    private boolean[] ct$heldAngleActive;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the held angle
    @Override
    public boolean ct$setHeldAngle(Direction.Axis axis, float angleDegrees) {
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo((KineticBlockEntity) (Object) this)) {
            return false;
        }
        ct$ensureHeldAngleStorage();
        int idx = axis.ordinal();
        float normalized = KineticAngleHelper.normalizeDegrees(angleDegrees);
        if (ct$heldAngleActive[idx] && Math.abs(ct$heldAngles[idx] - normalized) < 0.05f) {
            return false;
        }
        ct$heldAngles[idx] = normalized;
        ct$heldAngleActive[idx] = true;
        return true;
    }

    // Clear the held angles
    @Override
    public boolean ct$clearHeldAngles() {
        ct$ensureHeldAngleStorage();
        boolean hadAny = false;
        for (boolean active : ct$heldAngleActive) {
            if (active) {
                hadAny = true;
                break;
            }
        }
        if (!hadAny) {
            return false;
        }
        Arrays.fill(ct$heldAngleActive, false);
        Arrays.fill(ct$heldAngles, 0.0f);
        return true;
    }

    // Check if this has held angle
    @Override
    public boolean ct$hasHeldAngle(Direction.Axis axis) {
        ct$ensureHeldAngleStorage();
        return ct$heldAngleActive[axis.ordinal()];
    }

    // Get the absolute rotation angle
    @Override
    public float ct$getAbsoluteRotationAngle(Direction.Axis axis) {
        ct$ensureHeldAngleStorage();
        int idx = axis.ordinal();
        if (ct$heldAngleActive[idx]) {
            return ct$heldAngles[idx];
        }

        int liveOffset = ((KineticBlockEntity) (Object) this).getRotationAngleOffset(axis);
        return KineticAngleHelper.normalizeDegrees((float) liveOffset);
    }

    // Handle the held angle offset
    @Inject(method = "getRotationAngleOffset", at = @At("HEAD"), cancellable = true)
    private void ct$getHeldAngleOffset(Direction.Axis axis, CallbackInfoReturnable<Integer> cir) {
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo((KineticBlockEntity) (Object) this)) {
            return;
        }
        ct$ensureHeldAngleStorage();
        int idx = axis.ordinal();
        if (ct$heldAngleActive[idx]) {
            cir.setReturnValue(Math.round(ct$heldAngles[idx]));
        }
    }

    // Write the held angles
    @Inject(method = "write", at = @At("TAIL"))
    private void ct$writeHeldAngles(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo((KineticBlockEntity) (Object) this)) {
            return;
        }
        ct$ensureHeldAngleStorage();
        int activeMask = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            int idx = axis.ordinal();
            if (!ct$heldAngleActive[idx]) {
                continue;
            }
            activeMask |= 1 << idx;
            compound.putFloat("CTHeldAngle" + axis.name(), ct$heldAngles[idx]);
            compound.putFloat("CTAbsoluteRotationAngle" + axis.name(), ct$heldAngles[idx]);
        }
        if (activeMask != 0) {
            compound.putInt("CTHeldAngleMask", activeMask);
            compound.putInt("CTAbsoluteRotationAngleMask", activeMask);
        }
    }

    // Read the held angles
    @Inject(method = "read", at = @At("TAIL"))
    private void ct$readHeldAngles(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        ct$ensureHeldAngleStorage();
        Arrays.fill(ct$heldAngleActive, false);
        Arrays.fill(ct$heldAngles, 0.0f);
        if (!GadgetsNGizmosKineticGuard.shouldApplyHeldAngleTo((KineticBlockEntity) (Object) this)) {
            return;
        }
        int activeMask = compound.contains("CTAbsoluteRotationAngleMask")
                ? compound.getInt("CTAbsoluteRotationAngleMask")
                : compound.getInt("CTHeldAngleMask");
        if (activeMask == 0) {
            return;
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            int idx = axis.ordinal();
            if ((activeMask & (1 << idx)) == 0) {
                continue;
            }
            ct$heldAngleActive[idx] = true;
            String absoluteKey = "CTAbsoluteRotationAngle" + axis.name();
            String heldKey = "CTHeldAngle" + axis.name();
            ct$heldAngles[idx] = KineticAngleHelper.normalizeDegrees(compound.contains(absoluteKey)
                    ? compound.getFloat(absoluteKey)
                    : compound.getFloat(heldKey));
        }
    }

    // Ensure the held angle storage
    @Unique
    private void ct$ensureHeldAngleStorage() {
        int axisCount = Direction.Axis.values().length;
        if (ct$heldAngles == null || ct$heldAngles.length != axisCount) {
            ct$heldAngles = new float[axisCount];
        }
        if (ct$heldAngleActive == null || ct$heldAngleActive.length != axisCount) {
            ct$heldAngleActive = new boolean[axisCount];
        }
    }
}
