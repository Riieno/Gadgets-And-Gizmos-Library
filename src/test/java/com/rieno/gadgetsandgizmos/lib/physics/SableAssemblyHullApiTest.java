package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SableAssemblyHullApiTest{
    @Test
    void couplingUnpoweredCargoExtendsTheReverseCollisionEnvelope(){
        var lead = body(new Pose3d());
        Pose3d cargoPose = new Pose3d();
        cargoPose.position().set(0, 0, -12);
        var cargo = body(cargoPose);
        var single = SableAssemblyHullApi.sample(lead, List.of(lead));
        var train = SableAssemblyHullApi.sample(lead, List.of(lead, cargo));
        assertTrue(train.changedFrom(single, 0.5D));
        assertEquals(2, train.worldBounds().size());
        double clearance = SubLevelParticleOcclusion.findEnvelopeBlockingDistance(new Vec3(0, 0, -1), 10,
                train.worldBounds(), List.of(new AABB(-2, 0, -16, 2, 3, -15)));
        assertEquals(1.0D, clearance, 1.0E-6D);
    }

    @Test
    void livePlotGrowthAndCarriageSwayInvalidateTheOldShape(){
        var lead = body(new Pose3d());
        Pose3d pose = new Pose3d();
        pose.position().set(0, 0, -12);
        var cargo = body(pose);
        var original = SableAssemblyHullApi.sample(lead, List.of(lead, cargo));
        pose.position().add(3, 0, 0);
        var swayed = SableAssemblyHullApi.sample(lead, List.of(lead, cargo));
        assertTrue(swayed.changedFrom(original, 0.5D));
        when(cargo.getPlot().getBoundingBox()).thenReturn(new BoundingBox3i(-3, 0, -2, 3, 2, 2));
        assertTrue(SableAssemblyHullApi.sample(lead, List.of(lead, cargo)).changedFrom(swayed, 0.5D));
    }

    @Test
    void sharedTravelDoesNotInvalidateTheRootFrameShape(){
        Pose3d leadPose = new Pose3d();
        Pose3d cargoPose = new Pose3d();
        cargoPose.position().set(0, 0, -12);
        var lead = body(leadPose);
        var cargo = body(cargoPose);
        var original = SableAssemblyHullApi.sample(lead, List.of(lead, cargo));
        leadPose.position().add(50, 5, 0);
        cargoPose.position().add(50, 5, 0);
        assertFalse(SableAssemblyHullApi.sample(lead, List.of(lead, cargo)).changedFrom(original, 0.01D));
    }

    // Cached world bounds are absent so these tests require current plot geometry
    private static ServerSubLevel body(Pose3d pose){
        ServerSubLevel body = mock(ServerSubLevel.class);
        ServerLevelPlot plot = mock(ServerLevelPlot.class);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.logicalPose()).thenReturn(pose);
        when(body.getPlot()).thenReturn(plot);
        when(plot.getBoundingBox()).thenReturn(new BoundingBox3i(-1, 0, -2, 1, 2, 2));
        return body;
    }
}
