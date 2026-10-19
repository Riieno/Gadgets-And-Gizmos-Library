package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.kinetics.DirectionalPreciseKineticOutputAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputBoundary;
import com.rieno.gadgetsandgizmos.lib.kinetics.SequencedShaftAngle;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep exact output turns across Create sequenced gearshift instructions
@Mixin(SequencedGearshiftBlockEntity.class)
public abstract class SequencedGearshiftPreciseAngleOutputMixin implements PreciseKineticOutputBoundary{
    @Shadow
    float currentInstructionProgress;

    @Shadow
    int currentInstruction;

    @Unique
    private final SequencedShaftAngle ct$sequenceAngle = new SequencedShaftAngle();

    @Unique
    private long ct$nextIdleAngleTick;

    // Limit physical travel before Create advances or finishes the current instruction
    @Inject(method = "tick", at = @At("HEAD"))
    private void ct$advanceSequencedGearshiftAngle(CallbackInfo ci){
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity)(Object)this;
        if(self.getLevel() == null || self.getLevel().isClientSide) return;
        ct$sequenceAngle.advance(self.getSpeed() * self.getModifier());
    }

    // Start each instruction relative to the completed output angle
    @Inject(method = "run", at = @At("RETURN"))
    private void ct$startSequencedGearshiftTurn(int instructionIndex, CallbackInfo ci){
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity)(Object)this;
        if(self.getLevel() == null || self.getLevel().isClientSide) return;
        ct$sequenceAngle.start(ct$getTravelLimit(self));
        ct$nextIdleAngleTick = Long.MIN_VALUE;
    }

    // Publish on the output shaft even after its speed modifier returns to zero
    @Inject(method = "tick", at = @At("RETURN"))
    private void ct$publishSequencedGearshiftAngle(CallbackInfo ci){
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity)(Object)this;
        if(self.getLevel() == null || self.getLevel().isClientSide || !self.hasSource()
                || !GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) return;
        long now = self.getLevel().getGameTime();
        if(self.isIdle() && now < ct$nextIdleAngleTick) return;
        ct$nextIdleAngleTick = now + 10L;
        if(self instanceof DirectionalPreciseKineticOutputAccess access){
            access.ct$applyDirectionalPreciseAngleOutput(self.getSourceFacing().getOpposite(),
                    (float)ct$sequenceAngle.angleDegrees());
        }
    }

    @Unique
    private double ct$getTravelLimit(SequencedGearshiftBlockEntity self){
        var instruction = self.getInstruction(currentInstruction);
        if(instruction == null || self.getModifier() == 0) return 0.0D;
        var access = (SequencerInstructionAccessor)(Object)instruction;
        return access.ct$getInstruction() == SequencerInstructions.TURN_ANGLE
                ? Math.max(0, access.ct$getValue()) : Double.POSITIVE_INFINITY;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void ct$writeSequencedGearshiftAngle(CompoundTag tag, HolderLookup.Provider registries,
                                                boolean clientPacket, CallbackInfo ci){
        tag.putDouble("CTSequenceOutputAngle", ct$sequenceAngle.angleDegrees());
        tag.putDouble("CTSequenceRemainingAngle", ct$sequenceAngle.remainingDegrees());
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void ct$readSequencedGearshiftAngle(CompoundTag tag, HolderLookup.Provider registries,
                                               boolean clientPacket, CallbackInfo ci){
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity)(Object)this;
        double angle = tag.getDouble("CTSequenceOutputAngle");
        double remaining = tag.getDouble("CTSequenceRemainingAngle");
        if(!tag.contains("CTSequenceOutputAngle")){
            var state = self.getBlockState();
            var axis = ((IRotate)state.getBlock()).getRotationAxis(state);
            double heldAngle = KineticAngleHelper.getHeldRotationAngleDegrees(self, axis);
            angle = Double.isFinite(heldAngle) ? heldAngle : 0.0D;
            remaining = Math.max(0.0D, ct$getTravelLimit(self)
                    - Math.abs(currentInstructionProgress * self.getModifier()));
        }
        ct$sequenceAngle.restore(Double.isFinite(angle) ? angle : 0.0D,
                Double.isNaN(remaining) || remaining < 0.0D ? 0.0D : remaining);
    }
}
