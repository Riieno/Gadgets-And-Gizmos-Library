package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// Expose a live boolean probe as one read-only graph output
public final class ReadOnlyBooleanDataAdapter<T extends BlockEntity> implements BlockEntityDataAdapter<T> {
    private final Class<T> targetType;
    private final List<BlockEntityDataPort> ports;
    private final Predicate<? super T> probe;

    public ReadOnlyBooleanDataAdapter(Class<T> targetType, String port, Predicate<? super T> probe){
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.ports = List.of(BlockEntityDataPort.readable(port, "boolean"));
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public Class<T> targetType(){
        return targetType;
    }

    @Override
    public List<BlockEntityDataPort> ports(T target){
        return ports;
    }

    // Read the current state on every sample without caching it
    @Override
    public GraphValue read(T target, String port){
        if(target == null || !ports.getFirst().id().equals(port)) return null;
        return GraphValue.bool(probe.test(target));
    }
}
