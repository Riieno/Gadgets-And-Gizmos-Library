package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.time.ClockTime;
import com.simibubi.create.content.kinetics.clock.CuckooClockBlockEntity;

import java.util.List;

// Read the same world clock used by Create's cuckoo clock hands
public final class CuckooClockDataAdapter implements BlockEntityDataAdapter<CuckooClockBlockEntity>{
    private static final List<BlockEntityDataPort> PORTS = List.of(BlockEntityDataPort.readable("time", "string"));

    // Bind both normal and mysterious cuckoo clocks through their shared block entity
    @Override
    public Class<CuckooClockBlockEntity> targetType(){ return CuckooClockBlockEntity.class; }

    // Retain the clock's existing kinetic and state data alongside its time output
    @Override
    public boolean isSupplemental(){ return true; }

    // Expose a read-only clock output
    @Override
    public List<BlockEntityDataPort> ports(CuckooClockBlockEntity target){ return PORTS; }

    // Match Create's accelerated clock in dimensions without a natural day cycle
    @Override
    public GraphValue read(CuckooClockBlockEntity target, String port){
        if(target == null || target.isRemoved() || target.getLevel() == null || !"time".equals(port)) return null;
        var level = target.getLevel();
        long ticks = Math.floorMod(level.getDayTime(), 24000L);
        if(!level.dimensionType().natural()) ticks = ticks * 24 % 24000L;
        return GraphValue.string(ClockTime.world(ticks).text());
    }
}
