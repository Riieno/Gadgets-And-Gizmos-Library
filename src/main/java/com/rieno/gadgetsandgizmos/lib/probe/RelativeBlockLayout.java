package com.rieno.gadgetsandgizmos.lib.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

// Match a bounded arrangement without loading chunks or retaining a level
public final class RelativeBlockLayout{
    private final Map<BlockPos, Predicate<BlockState>> entries;

    public RelativeBlockLayout(Map<BlockPos, Predicate<BlockState>> entries){
        Map<BlockPos, Predicate<BlockState>> copy = new LinkedHashMap<>();
        entries.forEach((offset, predicate) -> copy.put(offset.immutable(), predicate));
        this.entries = Map.copyOf(copy);
    }

    // A missing state means the caller could not inspect a loaded position
    public boolean matches(BlockPos origin, Function<BlockPos, BlockState> states){
        for(var entry : entries.entrySet()){
            BlockState state = states.apply(origin.offset(entry.getKey()));
            if(state == null || !entry.getValue().test(state)) return false;
        }
        return true;
    }
}
