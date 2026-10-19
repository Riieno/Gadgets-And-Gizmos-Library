package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Verify independent drive allocation and live geometry across changing ship configurations
class ScmDriveRegressionTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Balance asymmetric drives rather than sharing one throttle
    @Test void balancesUnequalDriveBranches(){
        List<ScmSpeedGroupAllocator.Influence> drives = List.of(
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 100, 0), new Vec3(-2, 0, 0), Vec3.ZERO),
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 200, 0), new Vec3(2, 0, 0), Vec3.ZERO));
        double[] controls = ScmSpeedGroupAllocator.allocatePhysical(drives, new Vec3(0, 100, 0), Vec3.ZERO, 1);
        assertEquals(0.5, controls[0], 0.015);
        assertEquals(0.25, controls[1], 0.015);
    }

    // Increase holding thrust immediately when mass increases
    @Test void respondsToAddedMass(){
        var drives = List.of(new ScmSpeedGroupAllocator.Influence(new Vec3(0, 1000, 0), Vec3.ZERO));
        double light = ScmSpeedGroupAllocator.allocatePhysical(drives, new Vec3(0, 200, 0), Vec3.ZERO, 1)[0];
        double heavy = ScmSpeedGroupAllocator.allocatePhysical(drives, new Vec3(0, 600, 0), Vec3.ZERO, 1)[0];
        assertEquals(0.2, light, 0.01);
        assertEquals(0.6, heavy, 0.01);
        assertEquals(0, ScmSpeedGroupAllocator.allocatePhysical(drives, new Vec3(0, 600, 0), Vec3.ZERO, 0)[0]);
    }

    // Rebalance lift after new blocks move the center of mass sideways
    @Test void followsChangedCenterOfMass(){
        Vec3 center = new Vec3(1, 0, 0);
        var drives = List.of(
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 100, 0), new Vec3(-2, 0, 0), center),
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 100, 0), new Vec3(2, 0, 0), center));
        double[] controls = ScmSpeedGroupAllocator.allocatePhysical(drives, new Vec3(0, 100, 0), Vec3.ZERO, 1);
        assertEquals(0.25, controls[0], 0.015);
        assertEquals(0.75, controls[1], 0.015);
    }

    // Keep rotation-only commands available to separate acceleration controls
    @Test void allocatesPureYaw(){
        var drives = List.of(
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 0, 100), new Vec3(-2, 0, 0), Vec3.ZERO),
                ScmSpeedGroupAllocator.Influence.at(new Vec3(0, 0, -100), new Vec3(2, 0, 0), Vec3.ZERO));
        double[] controls = ScmSpeedGroupAllocator.allocatePhysical(drives, Vec3.ZERO, new Vec3(0, 200, 0), 1);
        assertEquals(0.5, controls[0], 0.015);
        assertEquals(0.5, controls[1], 0.015);
    }

    // Transform saved points and normals when a bearing's host moves
    @Test void movesCalibratedHostGeometry(){
        var saved = new ScmGeometryFrame(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        var live = new ScmGeometryFrame(new Vec3(10, 0, 0), new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1));
        assertEquals(new Vec3(10, 2, 0), saved.positionIn(live, new Vec3(2, 0, 0)));
        assertEquals(new Vec3(-1, 0, 0), saved.directionIn(live, new Vec3(0, 1, 0)));
        assertEquals(live, ScmGeometryFrame.read(live.write()));
        assertNull(ScmGeometryFrame.read(new net.minecraft.nbt.CompoundTag()));
    }

    // Rebuild acceleration authority when either mass or inertia changes
    @Test void updatesMassAndInertiaModel(){
        var unit = new ScmPrecisionAllocator.Unit(new Vec3(0, 100, 0), new Vec3(0, 0, 40), false);
        var light = ScmAdaptiveStateModel.forceActuators(unit, 10,
                new SableAssemblyDynamicsApi.Tensor(1, 0, 0, 0, 1, 0, 0, 0, 1), 0.5);
        var heavy = ScmAdaptiveStateModel.forceActuators(unit, 20,
                new SableAssemblyDynamicsApi.Tensor(0.5, 0, 0, 0, 0.5, 0, 0, 0, 0.5), 0.5);
        var first = ScmAdaptiveStateModel.sample(light, 0.05, 0.01, 0.01);
        var next = ScmAdaptiveStateModel.sample(heavy, 0.05, 0.01, 0.01);
        assertNotSame(first, next);
        assertEquals(10, first.inputMatrix()[7][0]);
        assertEquals(5, next.inputMatrix()[7][0]);
        assertEquals(20, next.inputMatrix()[11][0]);
    }

    // Traverse separate branches across frames while respecting independent control boundaries
    @Test void excludesOtherSpeedControlBranches(){
        Map<String, List<String>> links = Map.of("drive", List.of("gear", "other"),
                "gear", List.of("head"), "other", List.of("other_head"));
        var search = new ScmControlInfluenceGraph.Search<>("drive", key -> links.getOrDefault(key, List.of()),
                key -> key.endsWith("head"), "other"::equals, 32);
        search.advance(1, 1_000_000_000);
        assertFalse(search.finished());
        search.advance(32, 1_000_000_000);
        assertTrue(search.complete());
        assertEquals(List.of("head"), search.outputs());
        assertEquals(3, ScmPoseControl.advance(0, 90, 60, 0.05));
    }
}
