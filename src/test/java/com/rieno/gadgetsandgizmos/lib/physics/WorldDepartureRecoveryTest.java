package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldDepartureRecoveryTest{
    private final Level level = mock(Level.class);
    private final BlockState air = mock(BlockState.class);
    private final BlockState solid = mock(BlockState.class);
    private final List<AABB> hull = List.of(new AABB(0.1D, 0.7D, 0.1D, 0.9D, 2.4D, 0.9D));

    private void terrain(boolean ceiling){
        when(air.isAir()).thenReturn(true);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() == 0 || ceiling && pos.getY() == 3 ? solid : air;
        });
    }

    private double departure(Vec3 direction){
        return SubLevelParticleOcclusion.findAirOnlyDepartureBlockingDistance(level,
                direction, 4.0D, hull, true, Set.of(), 36, null, 0.1D);
    }

    @Test void groundOverlapAllowsAnOutwardClimbWithoutWeakeningStrictQueries(){
        terrain(false);
        Vec3 climb = new Vec3(0.1D, 1.0D, 0.0D).normalize();
        assertEquals(0.0D, SubLevelParticleOcclusion.findAirOnlyBoundsBlockingDistance(
                level, climb, 4.0D, hull, true, Set.of(), 36, null, 0.1D));
        assertEquals(4.0D, departure(climb), 1.0E-8D);
    }

    @Test void recoveryStillStopsForANewCeiling(){
        terrain(true);
        double distance = departure(new Vec3(0.1D, 1.0D, 0.0D));
        assertTrue(distance > 0.0D && distance < 1.0D, "ceiling clearance: " + distance);
    }

    @Test void recoveryCannotPushFurtherIntoTheGround(){
        terrain(false);
        assertEquals(0.0D, departure(new Vec3(0.0D, -1.0D, 0.0D)));
        assertTrue(departure(new Vec3(1.0D, 0.0D, 0.0D)) < 0.3D);
    }
}
