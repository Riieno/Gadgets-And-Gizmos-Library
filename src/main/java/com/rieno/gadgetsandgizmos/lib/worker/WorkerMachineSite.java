package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

// Describe one bounded machine and its explicitly marked external transfer ports
public record WorkerMachineSite(WorkerArea area, List<Port> inputs, List<Port> outputs){
    public WorkerMachineSite{
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    public record Port(BlockPos pos, Direction face){
        public Port{
            if(pos == null || face == null) throw new IllegalArgumentException("A machine port needs a block and face");
            pos = pos.immutable();
        }
    }
}
