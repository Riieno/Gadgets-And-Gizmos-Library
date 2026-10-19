package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ControllerTargetMapTest{
    @Test void namedTargetsAreDistinctAndKeepCallerOrder(){
        var first = target("a", "Bank", BlockPos.ZERO);var second = target("b", "bank", BlockPos.ZERO.east());
        assertEquals(List.of(second, first), ControllerDiscoveryService.allNamed(List.of(second, first, first), " BANK "));
        assertTrue(ControllerDiscoveryService.allNamed(List.of(first), "Ban").isEmpty());
        assertTrue(ControllerDiscoveryService.allNamed(null, "Bank").isEmpty());
    }

    @Test void mapsPreserveTypedTargetsAndDeterministicFirstSelection(){
        var first = target("z", "Bank", BlockPos.ZERO);var second = target("a", "Bank", BlockPos.ZERO.east());
        CompoundTag map = ControllerTargetMap.encode(List.of(first, second));
        var decoded = ControllerTargetMap.read(map.copy());
        assertEquals(List.of("z", "a"), decoded.stream().map(ControllerDiscoveryNode::nodeId).toList());
        assertEquals(first.toTag(), ControllerTargetMap.first(map));
        assertEquals(first.toTag(), ControllerTargetMap.first(first.toTag()));
        assertEquals(first.subLevelId(), decoded.getFirst().subLevelId());
        map.putString("noise", "ignored");map.put("broken", new CompoundTag());
        assertEquals(2, ControllerTargetMap.read(map).size());
        assertTrue(ControllerTargetMap.first(new CompoundTag()).isEmpty());
    }

    private static ControllerDiscoveryNode target(String id, String label, BlockPos pos){
        return new ControllerDiscoveryNode(id, ControllerDiscoveryKind.MACHINE, "", "minecraft:stone", label, UUID.randomUUID(), pos);
    }
}
