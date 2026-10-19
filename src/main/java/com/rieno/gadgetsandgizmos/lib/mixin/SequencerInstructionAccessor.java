package com.rieno.gadgetsandgizmos.lib.mixin;

import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Read the configured travel without relying on transient gearshift speeds
@Mixin(Instruction.class)
public interface SequencerInstructionAccessor{
    @Accessor("instruction")
    SequencerInstructions ct$getInstruction();

    @Accessor("value")
    int ct$getValue();
}
