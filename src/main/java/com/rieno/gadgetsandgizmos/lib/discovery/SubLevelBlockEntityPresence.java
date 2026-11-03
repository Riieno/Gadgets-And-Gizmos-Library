package com.rieno.gadgetsandgizmos.lib.discovery;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Cache loaded block entity presence without retaining a body, level or block entity
public final class SubLevelBlockEntityPresence{
    private final Predicate<BlockEntity> filter;
    private final Map<SubLevel, Entry> entries = new WeakHashMap<>();

    // Retain the host's block entity selection rules
    public SubLevelBlockEntityPresence(Predicate<BlockEntity> filter){ this.filter = filter; }

    // Recheck known matches directly and scan missing matches at most once per game tick
    public synchronized boolean matches(SubLevel body){
        if(body == null || body.isRemoved()) return false;
        long tick = body.getLevel().getGameTime();
        Entry entry = entries.get(body);
        if(entry != null){
            if(entry.pos() != null){
                BlockEntity be = SubLevelBlockEntityCollector.getBlockEntity(body, entry.pos());
                if(be != null && !be.isRemoved() && filter.test(be)) return true;
            }else if(entry.tick() == tick) return false;
        }
        for(BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(body, filter)){
            if(be.isRemoved()) continue;
            entries.put(body, new Entry(tick, be.getBlockPos().immutable()));
            return true;
        }
        entries.put(body, new Entry(tick, null));
        return false;
    }

    // Retain only coordinates and time so weak body keys can be collected
    private record Entry(long tick, BlockPos pos){}
}
