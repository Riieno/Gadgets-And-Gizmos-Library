package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScmHoldingAllocationTest{
    @Test
    void cachedHoldingControlsRemainIndependentAndRefreshChangedActuators(){
        Vec3 up = new Vec3(0, 1, 0);
        List<ScmPrecisionAllocator.Unit> units = new ArrayList<>(List.of(
                new ScmPrecisionAllocator.Unit(up.scale(10), Vec3.ZERO, false)));
        var first = ScmPrecisionAllocator.allocate(units, up.scale(5), Vec3.ZERO, null);
        double[] edited = first.controls();
        edited[0] = 0;
        var repeated = ScmPrecisionAllocator.allocate(units, up.scale(5), Vec3.ZERO, null);
        assertArrayEquals(first.controls(), repeated.controls());
        units.set(0, new ScmPrecisionAllocator.Unit(up.scale(20), Vec3.ZERO, false));
        var changed = ScmPrecisionAllocator.allocate(units, up.scale(5), Vec3.ZERO, null);
        assertEquals(0.5D, first.controls()[0], 0.001D);
        assertEquals(0.25D, changed.controls()[0], 0.001D);
        var stronger = ScmPrecisionAllocator.allocate(units, up.scale(10), Vec3.ZERO, null);
        assertEquals(0.5D, stronger.controls()[0], 0.001D);
    }
}
