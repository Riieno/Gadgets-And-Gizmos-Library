package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.DirectionalPreciseKineticOutputAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputBoundary;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Make Aeroworks servo outputs preserve exact kinetic angles
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.content.servo.AbstractServoBlockEntity", remap = false)
public abstract class AeroworksServoPreciseAngleOutputMixin implements PreciseKineticOutputBoundary {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Unique
    private static final String CT_OUTPUT_FACE_KEY = "CTAeroworksServoOutputFace";

    @Unique
    private static final String CT_OUTPUT_ANGLE_KEY = "CTAeroworksServoOutputAngle";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Last output face
    @Unique
    private Direction ct$lastOutputFace;

    // Last output angle
    @Unique
    private float ct$lastOutputAngle;

    // Last output angle initialized state
    @Unique
    private boolean ct$lastOutputAngleInitialized;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the interpolated angle
    @Shadow
    public abstract float getInterpolatedAngle(float partialTicks);

    // Publish the aeroworks servo output
    @Inject(method = "tick", at = @At("TAIL"))
    private void ct$publishAeroworksServoOutput(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        if (!(self instanceof DirectionalPreciseKineticOutputAccess output)) {
            return;
        }
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            output.ct$clearDirectionalPreciseAngleOutput();
            return;
        }

        if (ct$rememberOutputFace(self)) {
            float controlledAngle = getInterpolatedAngle(1.0f);
            ct$lastOutputAngle = -controlledAngle * ct$lastOutputFace.getAxisDirection().getStep();
            ct$lastOutputAngleInitialized = true;
        }
        if (ct$lastOutputFace == null || !ct$lastOutputAngleInitialized) {
            output.ct$clearDirectionalPreciseAngleOutput();
            return;
        }

        output.ct$applyDirectionalPreciseAngleOutput(ct$lastOutputFace, ct$lastOutputAngle);
    }

    // Read the aeroworks servo output face
    @Inject(method = "read", at = @At("TAIL"))
    private void ct$readAeroworksServoOutputFace(CompoundTag compound, HolderLookup.Provider registries,
                                                 boolean clientPacket, CallbackInfo ci) {
        if (!compound.contains(CT_OUTPUT_FACE_KEY)) {
            ct$lastOutputFace = null;
            ct$lastOutputAngle = 0.0f;
            ct$lastOutputAngleInitialized = false;
            return;
        }
        int ordinal = compound.getInt(CT_OUTPUT_FACE_KEY);
        Direction[] directions = Direction.values();
        ct$lastOutputFace = ordinal >= 0 && ordinal < directions.length ? directions[ordinal] : null;
        ct$lastOutputAngleInitialized = compound.contains(CT_OUTPUT_ANGLE_KEY);
        ct$lastOutputAngle = ct$lastOutputAngleInitialized ? compound.getFloat(CT_OUTPUT_ANGLE_KEY) : 0.0f;
    }

    // Write the aeroworks servo output face
    @Inject(method = "write", at = @At("TAIL"))
    private void ct$writeAeroworksServoOutputFace(CompoundTag compound, HolderLookup.Provider registries,
                                                  boolean clientPacket, CallbackInfo ci) {
        if (ct$lastOutputFace != null) {
            compound.putInt(CT_OUTPUT_FACE_KEY, ct$lastOutputFace.ordinal());
        }
        if (ct$lastOutputAngleInitialized) {
            compound.putFloat(CT_OUTPUT_ANGLE_KEY, ct$lastOutputAngle);
        }
    }

    // Remember the Aeroworks output face
    @Unique
    private boolean ct$rememberOutputFace(KineticBlockEntity self) {
        if (!self.hasSource()) {
            return false;
        }
        DirectionalShaftHalvesBlockEntity directional = (DirectionalShaftHalvesBlockEntity) self;
        ct$lastOutputFace = directional.getSourceFacing().getOpposite();
        return true;
    }
}
