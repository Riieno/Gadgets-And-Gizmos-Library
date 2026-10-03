package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubLevelLocatorTest{
    @Test void unnamedSublevelCanBeIndexed(){
        var location = new SubLevelLocator.Location(UUID.randomUUID(), null, Vec3.ZERO, BlockPos.ZERO, 0, 0);
        assertEquals("", location.name());
    }
}
