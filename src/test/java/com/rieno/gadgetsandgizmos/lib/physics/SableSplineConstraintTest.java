package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.navigation.SplineConstraintFrame;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SableSplineConstraintTest{
    private static final Vec3 FORWARD = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final WaypointSpline SPLINE = WaypointSpline.of(List.of(
            Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D)));

    // Load vanilla registries before creating server-level test doubles
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(MockedStatic<net.neoforged.fml.loading.LoadingModList> loader =
                    mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var modList = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(modList.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(modList);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void groundAttachmentLocksOnlyCrossTrackAndReusesItsHandle() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    (pos, dir, step) -> true));
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    (pos, dir, step) -> true));
            ArgumentCaptor<GenericConstraintConfiguration> config =
                    ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
            verify(ctx.pipeline, times(1)).addConstraint(isNull(), same(ctx.body), config.capture());
            assertTrue(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_X));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_Y));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_Z));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_X));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_Y));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_Z));
            verify(ctx.handle, never()).setFrame1(any(), any());
            verify(ctx.handle, never()).setFrame2(any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    @Test
    void flightAttachmentLocksOnlyBothTransverseAxes() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            ArgumentCaptor<GenericConstraintConfiguration> config =
                    ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
            verify(ctx.pipeline).addConstraint(isNull(), same(ctx.body), config.capture());
            assertTrue(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_X));
            assertTrue(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_Y));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_X));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_Y));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.ANGULAR_Z));
            assertFalse(config.getValue().lockedAxes().contains(ConstraintJointAxis.LINEAR_Z));
        }
    }

    @Test
    void centrelineOverlapGuidesBeforePromotingToRigidCapture() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            ctx.pose.position().set(5.0D, 0.0D, 0.5D);
            Vec3 pos = new Vec3(ctx.pose.position().x, ctx.pose.position().y, ctx.pose.position().z);
            var capture = new SableSplineConstraint.CaptureSettings(
                    0.75D, 0.05D, Math.toRadians(8.0D), 0.75D,
                    18.0D, 8.0D, 8.0D, 1.25D);
            assertTrue(constraint.update(ctx.body, SPLINE, SPLINE.project(pos), Vec3.ZERO,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    capture, 2.0D, (position, dir, step) -> true));
            constraint.step(0.01D);
            assertEquals(SableSplineConstraint.Stage.GUIDING, constraint.stage());
            assertFalse(constraint.rigid());
            ArgumentCaptor<GenericConstraintConfiguration> config =
                    ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
            verify(ctx.pipeline).addConstraint(isNull(), same(ctx.body), config.capture());
            assertTrue(config.getValue().lockedAxes().isEmpty());
            verify(ctx.handle).setMotor(ConstraintJointAxis.LINEAR_X, 0.0D,
                    18.0D, 8.0D, true, 800.0D);
            verify(ctx.pipeline).wakeUp(ctx.body);

            ctx.pose.position().set(5.0D, 0.0D, 0.02D);
            constraint.step(0.01D);
            assertEquals(SableSplineConstraint.Stage.RIGID, constraint.stage());
            assertTrue(constraint.rigid());
            verify(ctx.pipeline, times(2)).addConstraint(isNull(), same(ctx.body), any());
        }
    }

    @Test
    void overlapCertifiedCaptureAttachesRigidlyDespiteRecoverableLateralMomentum()
            throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            when(ctx.rigidBody.getLinearVelocity(any(org.joml.Vector3d.class)))
                    .thenAnswer(call -> ((org.joml.Vector3d) call.getArgument(0))
                            .set(0.0D, 0.0D, 20.0D));
            Vec3 pos = new Vec3(ctx.pose.position().x, ctx.pose.position().y,
                    ctx.pose.position().z);
            var capture = new SableSplineConstraint.CaptureSettings(
                    8.0D, 1.5D, Math.toRadians(35.0D), 4.0D,
                    12.0D, 8.0D, 4.0D, 10.0D);
            assertTrue(constraint.updateCaptured(
                    ctx.body, SPLINE, SPLINE.project(pos), Vec3.ZERO,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    capture, 2.0D, (position, dir, step) -> true));
            constraint.step(0.01D);
            assertTrue(constraint.rigid());
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
        }
    }

    @Test
    void detachedCaptureUsesTheCurrentOverlappingLegInsteadOfAnOldCursor() throws Exception{
        WaypointSpline loop = WaypointSpline.of(List.of(
                Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D),
                new Vec3(20.0D, 0.0D, 20.0D), new Vec3(-20.0D, 0.0D, 20.0D)));
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            AtomicBoolean clear = new AtomicBoolean(true);
            ctx.pose.position().set(5.0D, 0.0D, 0.0D);
            assertTrue(constraint.updateCaptured(ctx.body, loop,
                    loop.project(new Vec3(5.0D, 0.0D, 0.0D)), Vec3.ZERO,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    new SableSplineConstraint.CaptureSettings(8.0D, 1.5D,
                            Math.toRadians(35.0D), 4.0D, 12.0D, 8.0D, 4.0D, 10.0D),
                    2.0D, (position, dir, step) -> clear.get()));
            constraint.step(0.01D);
            clear.set(false);
            constraint.step(0.01D);
            assertFalse(constraint.active());
            ctx.pose.position().set(5.0D, 0.0D, 20.0D);
            WaypointSpline.Projection current = loop.projectSegment(
                    new Vec3(5.0D, 0.0D, 20.0D), 2, 0.0D);
            clear.set(true);
            assertTrue(constraint.updateCaptured(ctx.body, loop, current, Vec3.ZERO,
                    new Vec3(-1.0D, 0.0D, 0.0D), UP,
                    SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    new SableSplineConstraint.CaptureSettings(8.0D, 1.5D,
                            Math.toRadians(35.0D), 4.0D, 12.0D, 8.0D, 4.0D, 10.0D),
                    2.0D, (position, dir, step) -> true));
            constraint.step(0.01D);
            assertTrue(constraint.active(), constraint.diagnostic());
            ArgumentCaptor<GenericConstraintConfiguration> configs =
                    ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
            verify(ctx.pipeline, times(2)).addConstraint(isNull(), same(ctx.body),
                    configs.capture());
            assertTrue(configs.getAllValues().getLast().pos1().z() > 18.0D);
        }
    }

    @Test
    void worldAnchorUsesNativeMotionWithTheSubLevelPlotFrame() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            Pose3d staleLogicalPose = new Pose3d();
            staleLogicalPose.rotationPoint().set(2048.0D, 64.0D, 4096.0D);
            when(ctx.body.logicalPose()).thenReturn(staleLogicalPose);
            Vec3 worldAnchor = new Vec3(ctx.pose.position().x,
                    ctx.pose.position().y, ctx.pose.position().z);
            var capture = new SableSplineConstraint.CaptureSettings(
                    8.0D, 1.5D, Math.toRadians(35.0D), 4.0D,
                    12.0D, 8.0D, 4.0D, 10.0D);
            assertTrue(constraint.updateCapturedWorldAnchor(
                    ctx.body, SPLINE, SPLINE.project(worldAnchor), worldAnchor,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    capture, 2.0D, (position, dir, step) -> true));
            constraint.step(0.01D);
            assertTrue(constraint.rigid());
            ArgumentCaptor<GenericConstraintConfiguration> config =
                    ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
            verify(ctx.pipeline).addConstraint(isNull(), same(ctx.body), config.capture());
            assertEquals(2048.0D, config.getValue().pos2().x(), 1.0E-8D);
            assertEquals(64.0D, config.getValue().pos2().y(), 1.0E-8D);
            assertEquals(4096.0D, config.getValue().pos2().z(), 1.0E-8D);
        }
    }

    @Test
    void hazardReleasesBeforeTheNextPhysicsStepWithoutResettingMomentum() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            AtomicBoolean clear = new AtomicBoolean(true);
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> clear.get()));
            clear.set(false);
            constraint.step(0.01D);
            assertFalse(constraint.active());
            verify(ctx.handle).remove();
            verify(ctx.pipeline, never()).resetVelocity(any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    @Test
    void missingRouteCannotCreateAnAttachment() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertFalse(constraint.update(ctx.body, null, WaypointSpline.Projection.notFound(),
                    Vec3.ZERO, FORWARD, UP, SplineConstraintFrame.AxisPolicy.ALL,
                    2.0D, (pos, dir, step) -> true));
            verify(ctx.pipeline, never()).addConstraint(any(), any(), any());
        }
    }

    @Test
    void attachedConstraintNeverInjectsVelocity() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    (pos, dir, step) -> true));
            constraint.step(0.01D);
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    @Test
    void terminalHandoffRemovesTheAttachment() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            ctx.pose.position().set(19.0D, 0.0D, 0.0D);
            constraint.step(0.01D);
            assertFalse(constraint.active());
            verify(ctx.handle).remove();
        }
    }

    @Test
    void highSpeedOvershootReleasesBeforeRetargetingTheRigidJoint() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            ctx.pose.position().set(17.5D, 0.0D, 0.0D);
            when(ctx.rigidBody.getLinearVelocity(any(org.joml.Vector3d.class)))
                    .thenAnswer(call -> ((org.joml.Vector3d) call.getArgument(0)).set(60.0D, 0.0D, 0.0D));
            constraint.step(0.01D);
            assertFalse(constraint.active());
            verify(ctx.handle).remove();
            verify(ctx.handle, never()).setFrame1(any(), any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    @Test
    void replacesTheFrameOnACurveWithoutMovingItsSolverAnchor() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            GenericConstraintHandle replacement = mock(GenericConstraintHandle.class);
            when(replacement.isValid()).thenReturn(true);
            when(ctx.pipeline.addConstraint(isNull(), same(ctx.body),
                    any(GenericConstraintConfiguration.class)))
                    .thenReturn(ctx.handle, replacement);
            WaypointSpline curve = WaypointSpline.of(List.of(Vec3.ZERO,
                    new Vec3(10.0D, 0.0D, 0.0D), new Vec3(10.0D, 0.0D, 10.0D)));
            Vec3 pos = curve.segments().getFirst().pointAtFraction(0.8D);
            ctx.pose.position().set(pos.x, pos.y, pos.z);
            Vec3 initialDirection = curve.project(pos).tangent();
            assertTrue(constraint.update(ctx.body, curve, curve.project(pos), Vec3.ZERO,
                    initialDirection, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    2.0D, (position, dir, step) -> true));
            constraint.step(0.01D);
            assertTrue(constraint.active());
            when(ctx.rigidBody.getLinearVelocity(any(org.joml.Vector3d.class)))
                    .thenAnswer(call -> ((org.joml.Vector3d) call.getArgument(0)).set(6.0D, 0.0D, 0.0D));
            Vec3 next = curve.segments().get(1).pointAtFraction(0.2D);
            ctx.pose.position().set(next.x, next.y, next.z);
            constraint.step(0.01D);
            assertTrue(constraint.active());
            verify(ctx.handle).remove();
            verify(ctx.handle, never()).setFrame1(any(), any());
            verify(ctx.handle, never()).setFrame2(any(), any());
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
            verify(ctx.pipeline, times(2)).addConstraint(isNull(), same(ctx.body), any());
        }
    }

    @Test
    void repeatedGameUpdatesDoNotRewindThePhysicsOwnedFrame() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            ctx.pose.position().set(6.0D, 0.0D, 0.0D);
            constraint.step(0.01D);
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            verify(ctx.handle, never()).setFrame1(any(), any());
            verify(ctx.handle, never()).setFrame2(any(), any());
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
        }
    }

    @Test
    void removedBodyReleasesItsRetainedHandle() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(ctx.attach(constraint, SplineConstraintFrame.AxisPolicy.ALL,
                    (pos, dir, step) -> true));
            when(ctx.body.isRemoved()).thenReturn(true);
            constraint.step(0.01D);
            assertFalse(constraint.active());
            verify(ctx.handle).remove();
            verify(ctx.pipeline, never()).remove(ctx.body);
        }
    }

    @Test
    void lifecycleMutatesOnlyTheJointDuringPhysicsSteps() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            Vec3 pos = new Vec3(ctx.pose.position().x, ctx.pose.position().y, ctx.pose.position().z);
            assertTrue(constraint.update(ctx.body, SPLINE, SPLINE.project(pos), Vec3.ZERO,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    2.0D, (position, dir, step) -> true));
            assertEquals(1L, constraint.debugState().queuedUpdates());
            assertEquals(0L, constraint.debugState().physicsSteps());
            assertEquals(0L, constraint.debugState().jointInstalls());
            verify(ctx.pipeline, never()).addConstraint(any(), any(), any());
            constraint.step(0.01D);
            assertEquals(1L, constraint.debugState().physicsSteps());
            assertEquals(1L, constraint.debugState().jointInstalls());
            assertEquals(0L, constraint.debugState().jointReleases());
            verify(ctx.pipeline).addConstraint(isNull(), same(ctx.body), any());
            constraint.close();
            verify(ctx.handle, never()).remove();
            constraint.step(0.01D);
            assertEquals(1L, constraint.debugState().jointReleases());
            verify(ctx.handle).remove();
            verify(ctx.pipeline, never()).remove(ctx.body);
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
        }
    }

    @Test
    void sablePrePhysicsEventAppliesAQueuedAttachment() throws Exception{
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            Vec3 pos = new Vec3(ctx.pose.position().x,
                    ctx.pose.position().y, ctx.pose.position().z);
            assertTrue(constraint.update(
                    ctx.body, SPLINE, SPLINE.project(pos), Vec3.ZERO,
                    FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    2.0D, (position, dir, step) -> true));
            assertEquals("queued for physics", constraint.diagnostic());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.start();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent(
                            ctx.system, 0.01D));
            assertTrue(constraint.active());
            assertEquals("rigid", constraint.diagnostic());
            verify(ctx.pipeline).addConstraint(isNull(), same(ctx.body), any());
        }
    }

    @Test
    void prePhysicsListenerCanRegisterAfterTheGameEventBusStarts(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.start();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent.class,
                event -> calls.incrementAndGet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent(
                        mock(SubLevelPhysicsSystem.class), 0.01D));
        assertEquals(1, calls.get());
    }

    // Supply a loaded solver body without running native physics
    private static final class Fixture implements AutoCloseable{
        private final ServerLevel level = mock(ServerLevel.class);
        private final ServerSubLevel body = mock(ServerSubLevel.class);
        private final SubLevelPhysicsSystem system = mock(SubLevelPhysicsSystem.class);
        private final PhysicsPipeline pipeline = mock(PhysicsPipeline.class);
        private final GenericConstraintHandle handle = mock(GenericConstraintHandle.class);
        private final RigidBodyHandle rigidBody = mock(RigidBodyHandle.class);
        private final MassData mass = mock(MassData.class);
        private final Pose3d pose = new Pose3d();
        private final MockedStatic<SubLevelPhysicsSystem> systems = mockStatic(SubLevelPhysicsSystem.class);

        // Set up a body centred on the route
        private Fixture(){
            pose.position().set(5.0D, 0.0D, 0.0D);
            when(body.getLevel()).thenReturn(level);
            when(body.logicalPose()).thenReturn(pose);
            when(body.getMassTracker()).thenReturn(mass);
            when(mass.getMass()).thenReturn(100.0D);
            when(system.getPipeline()).thenReturn(pipeline);
            when(system.getPhysicsHandle(body)).thenReturn(rigidBody);
            when(rigidBody.isValid()).thenReturn(true);
            when(handle.isValid()).thenReturn(true);
            when(pipeline.readPose(same(body), any(Pose3d.class))).thenReturn(pose);
            systems.when(() -> SubLevelPhysicsSystem.get(level)).thenReturn(system);
            when(pipeline.addConstraint(isNull(), same(body), any(GenericConstraintConfiguration.class)))
                    .thenReturn(handle);
        }

        // Attach using the current body position
        private boolean attach(SableSplineConstraint constraint, SplineConstraintFrame.AxisPolicy axes,
                               SableSplineConstraint.ClearancePredicate clearance) throws Exception{
            Vec3 pos = new Vec3(pose.position().x, pose.position().y, pose.position().z);
            boolean accepted = constraint.update(body, SPLINE, SPLINE.project(pos), Vec3.ZERO,
                    FORWARD, UP, axes, 2.0D, clearance);
            constraint.step(0.01D);
            return accepted && constraint.active();
        }

        // Close the static physics-system bridge
        @Override
        public void close(){
            systems.close();
        }
    }
}
