package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import org.joml.Vector3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify geometry reuse and native attachment polling preserve immediate invalidation
class CollisionQueryOptimizationTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void cachedBoundsMatchAllTransformedCorners(){
        Pose3d pose = new Pose3d();
        BoundingBox3i local = new BoundingBox3i(2000000, -64, -2000000, 2000015, 8, -1999993);
        ServerSubLevel body = body(mock(ServerLevel.class), pose, local);
        Random random = new Random(4391);
        for(int idx = 0; idx < 128; idx++){
            pose.position().set(random.nextDouble() * 100, random.nextDouble() * 100, random.nextDouble() * 100);
            pose.rotationPoint().set(2000000, 0, -2000000);
            pose.orientation().identity().rotateXYZ(random.nextDouble(), random.nextDouble(), random.nextDouble());
            pose.scale().set(0.5D + random.nextDouble(), 0.5D + random.nextDouble(), 0.5D + random.nextDouble());
            AABB expected = corners(local, pose);
            AABB actual = SableAssemblyHullApi.worldBounds(body);
            assertBounds(expected, actual);
            assertSame(actual, SableAssemblyHullApi.worldBounds(body));
        }
    }

    @Test void editsAndEveryPoseComponentInvalidateWithinTheSameTick(){
        Pose3d pose = new Pose3d();
        BoundingBox3i local = new BoundingBox3i(0, 0, 0, 2, 3, 4);
        ServerSubLevel body = body(mock(ServerLevel.class), pose, local);
        AABB prev = SableAssemblyHullApi.worldBounds(body);
        for(Runnable edit : List.<Runnable>of(() -> pose.position().add(1, 2, 3),
                () -> pose.orientation().rotateY(0.4D), () -> pose.rotationPoint().set(1, 2, 3),
                () -> pose.scale().set(2, 1, 1), () -> local.maxX++)){
            edit.run();
            AABB next = SableAssemblyHullApi.worldBounds(body);
            assertNotSame(prev, next);
            assertBounds(corners(local, pose), next);
            prev = next;
        }
        local.minX = Integer.MAX_VALUE; local.maxX = Integer.MIN_VALUE;
        assertNull(SableAssemblyHullApi.worldBounds(body));
        local.minX = 0; local.maxX = 2;
        assertNotNull(SableAssemblyHullApi.worldBounds(body));
        when(body.isRemoved()).thenReturn(true);
        assertNull(SableAssemblyHullApi.worldBounds(body));
    }

    @Test void nativeValidityIsCheckedOnceUntilPhysicsInvalidatesIt(){
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel first = body(level, new Pose3d(), new BoundingBox3i());
        ServerSubLevel second = body(level, new Pose3d(), new BoundingBox3i());
        TestHandle handle = new TestHandle();
        SablePhysicalConnectionApi.track(first, second, handle);
        for(int idx = 0; idx < 32; idx++){
            SablePhysicalConnectionApi.revision(level);
            assertEquals(Set.of(first.getUniqueId(), second.getUniqueId()),
                    SablePhysicalConnectionApi.connectedBodyIds(level, Set.of(first.getUniqueId())));
        }
        assertEquals(1, handle.polls);
        handle.valid = false;
        SablePhysicalConnectionApi.invalidate(level);
        assertEquals(Set.of(first.getUniqueId()), SablePhysicalConnectionApi.connectedBodyIds(level, Set.of(first.getUniqueId())));
        assertEquals(2, handle.polls);
    }

    @Test void removalsAndMultipleJointEdgesRefreshOwnershipImmediately(){
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel first = body(level, new Pose3d(), new BoundingBox3i());
        ServerSubLevel second = body(level, new Pose3d(), new BoundingBox3i());
        TestHandle a = new TestHandle(), b = new TestHandle();
        SablePhysicalConnectionApi.track(first, second, a); SablePhysicalConnectionApi.track(first, second, b);
        assertEquals(2, SablePhysicalConnectionApi.connectedBodyIds(level, Set.of(first.getUniqueId())).size());
        long revision = SablePhysicalConnectionApi.revision(level);
        SablePhysicalConnectionApi.untrack(a);
        assertTrue(SablePhysicalConnectionApi.revision(level) > revision);
        assertEquals(2, SablePhysicalConnectionApi.connectedBodyIds(level, Set.of(first.getUniqueId())).size());
        SablePhysicalConnectionApi.untrack(second);
        assertEquals(Set.of(first.getUniqueId()), SablePhysicalConnectionApi.connectedBodyIds(level, Set.of(first.getUniqueId())));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static ServerSubLevel body(ServerLevel level, Pose3d pose, BoundingBox3i local){
        ServerSubLevel body = mock(ServerSubLevel.class);
        ServerLevelPlot plot = mock(ServerLevelPlot.class);
        when(body.getLevel()).thenReturn(level); when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getPlot()).thenReturn(plot); when(plot.getBoundingBox()).thenReturn(local);
        when(body.logicalPose()).thenReturn(pose);
        return body;
    }

    private static AABB corners(BoundingBox3i bounds, Pose3d pose){
        AABB res = null;
        for(int idx = 0; idx < 8; idx++){
            Vec3 point = pose.transformPosition(new Vec3((idx & 1) == 0 ? bounds.minX : bounds.maxX + 1.0D,
                    (idx & 2) == 0 ? bounds.minY : bounds.maxY + 1.0D, (idx & 4) == 0 ? bounds.minZ : bounds.maxZ + 1.0D));
            AABB corner = new AABB(point, point);
            res = res == null ? corner : res.minmax(corner);
        }
        return res;
    }

    private static void assertBounds(AABB expected, AABB actual){
        assertNotNull(actual);
        assertEquals(expected.minX, actual.minX, 1.0E-8D); assertEquals(expected.maxX, actual.maxX, 1.0E-8D);
        assertEquals(expected.minY, actual.minY, 1.0E-8D); assertEquals(expected.maxY, actual.maxY, 1.0E-8D);
        assertEquals(expected.minZ, actual.minZ, 1.0E-8D); assertEquals(expected.maxZ, actual.maxZ, 1.0E-8D);
    }

    private static final class TestHandle implements FixedConstraintHandle{
        private boolean valid = true;
        private int polls;
        @Override public boolean isValid(){ polls++; return valid; }
        @Override public void remove(){ valid = false; }
        @Override public void getJointImpulses(Vector3d linear, Vector3d angular){}
        @Override public void setContactsEnabled(boolean enabled){}
        @Override public void setMotor(ConstraintJointAxis axis, double position, double velocity, double stiffness,
                boolean usePosition, double damping){}
    }
}
