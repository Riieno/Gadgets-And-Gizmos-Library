package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Retain requested entrances until they can be closed on the owning game thread
public final class ShipDoorControl{
    private List<ShipDoorApi.Door> doors = List.of();
    private final Set<ShipDoorApi.Door> opened = new HashSet<>();

    public List<ShipDoorApi.Door> doors(){ return doors; }

    public void replace(List<ShipDoorApi.Door> mapped){ doors = List.copyOf(mapped); }

    // Close previous selections before opening the newly requested sides
    public void apply(Level level, int mask){
        Set<ShipDoorApi.Door> selected = new HashSet<>();
        for(var door : doors) if((mask & door.side().mask()) != 0) selected.add(door);
        opened.removeIf(door -> !selected.contains(door) && ShipDoorApi.setOpen(level, door, false));
        for(var door : selected){
            opened.add(door);
            ShipDoorApi.setOpen(level, door, true);
        }
    }

    public void close(Level level){ apply(level, 0); }

    // Recover the dock's selection when an endpoint disconnects before its first runtime tick
    public void close(Level level, int mask){
        for(var door : doors) if((mask & door.side().mask()) != 0) opened.add(door);
        close(level);
    }
}
