package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelBlockIndexTest{
    @Test
    void keepsFacesAtTheSameAddressAndSeparatesBodies(){
        UUID ship = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        BlockPos pos = new BlockPos(2, 3, 4);
        var north = new Reference(ship, pos, "north");
        var south = new Reference(ship, pos, "south");
        var remote = new Reference(other, pos, "north");
        var world = new Reference(null, pos, "north");
        var refs = new ArrayList<>(List.of(north, south, remote, world));
        var index = new SubLevelBlockIndex<>(refs, Reference::ship, Reference::pos);
        refs.clear();
        assertEquals(List.of(north, south), index.at(ship, pos));
        assertEquals(List.of(remote), index.at(other, pos));
        assertEquals(List.of(world), index.at(null, pos));
        assertTrue(index.at(ship, BlockPos.ZERO).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> index.at(ship, pos).clear());
    }

    private record Reference(UUID ship, BlockPos pos, String face){}
}
