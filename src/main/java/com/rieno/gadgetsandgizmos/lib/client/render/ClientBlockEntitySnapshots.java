package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// Retain detached client models for remotely synchronized blocks outside world tracking
public final class ClientBlockEntitySnapshots<B extends BlockEntity>{
    // Own snapshots for the current client level only
    private Level level;
    private final Map<Key, B> snapshots = new HashMap<>();

    // Reuse one detached model until its owning client level changes
    public B resolve(Level next, UUID subLevelId, BlockPos pos, Supplier<B> factory){
        if(next == null || !next.isClientSide || pos == null) return null;
        if(level != next){ clear(); level = next; }
        return snapshots.computeIfAbsent(new Key(subLevelId, pos.immutable()), key -> {
            B be = factory.get();
            be.setLevel(next);
            return be;
        });
    }

    // Release detached snapshots on disconnect or world changes
    public void clear(){ snapshots.clear(); level = null; }

    // Keep root-world and sublevel coordinates distinct
    private record Key(UUID subLevelId, BlockPos pos){}
}
