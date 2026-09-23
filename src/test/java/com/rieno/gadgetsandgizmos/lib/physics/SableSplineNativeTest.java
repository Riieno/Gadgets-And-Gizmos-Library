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
import org.joml.Quaterniondc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Exercise the production attachment against Sable's native solver
class SableSplineNativeTest{
    private static final Vec3 FORWARD = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final WaypointSpline CURVE = WaypointSpline.of(List.of(Vec3.ZERO,
            new Vec3(40.0D, 0.0D, 0.0D), new Vec3(40.0D, 0.0D, 120.0D),
            new Vec3(160.0D, 0.0D, 120.0D)));
    private static final Map<String, Method> METHODS = new HashMap<>();

    @BeforeAll
    static void bootstrap() throws Exception{
        SableSplineConstraintTest.bootstrap();
        Class<?> api = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
        for(Method method : api.getDeclaredMethods()){
            method.setAccessible(true);
            METHODS.put(method.getName(), method);
        }
    }

    @Test
    void groundAndFlightConstrainExternallyDrivenBodiesThroughCurves() throws Exception{
        for(var axes : SplineConstraintFrame.AxisPolicy.values()){
            for(double speed : new double[]{20.0D, 60.0D}){
                try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
                    assertTrue(constraint.update(ctx.body, CURVE, CURVE.project(Vec3.ZERO),
                            Vec3.ZERO, FORWARD, UP, axes, 2.0D, (pos, dir, step) -> true));
                    double strongestOffset = 0.0D;
                    double strongestSolverSpeedGain = 0.0D;
                    Vec3 worstPosition = Vec3.ZERO;
                    for(int idx = 0; idx < 300; idx++){
                        ctx.applyDrive(constraint.direction(), speed, 0.01D);
                        double speedBeforeSolver = ctx.speed();
                        constraint.step(0.01D);
                        assertTrue(constraint.active(), axes + " at " + speed
                                + " step " + idx + ": " + constraint.debugState());
                        invoke("step", ctx.scene, 0.01D);
                        strongestSolverSpeedGain = Math.max(strongestSolverSpeedGain,
                                ctx.speed() - speedBeforeSolver);
                        Vec3 position = ctx.position();
                        double offset = CURVE.project(position).distance();
                        if(offset > strongestOffset){
                            strongestOffset = offset;
                            worstPosition = position;
                        }
                        if(idx > 120) assertEquals(speed, ctx.speed(), speed * 0.075D);
                    }
                    assertTrue(ctx.position().distanceTo(Vec3.ZERO) > 30.0D);
                    assertTrue(strongestOffset < 0.10D, axes + " at " + speed
                            + ": native centreline offset " + strongestOffset + " at " + worstPosition);
                    assertTrue(strongestSolverSpeedGain < 1.0E-4D, axes + " at " + speed
                            + ": constraint added " + strongestSolverSpeedGain + " speed in one step");
                    // The game pose remains stale; only solver pose reads can track this curve.
                    assertEquals(Vec3.ZERO, ctx.body.logicalPose().transformPosition(Vec3.ZERO));
                    verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
                }
            }
        }
    }

    @Test
    void attachmentUsesTheNativePoseWithoutCreatingHandoffVelocity() throws Exception{
        try(Fixture ctx = new Fixture(new Vec3(0.0D, 0.0D, 0.04D));
            SableSplineConstraint constraint = new SableSplineConstraint()){
            assertTrue(constraint.update(ctx.body, CURVE, CURVE.project(Vec3.ZERO),
                    Vec3.ZERO, FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    2.0D, (pos, dir, step) -> true));
            for(int idx = 0; idx < 20; idx++){
                constraint.step(0.01D);
                invoke("step", ctx.scene, 0.01D);
            }
            assertEquals(0.0D, ctx.speed(), 1.0E-6D);
            assertEquals(0.0D, ctx.position().z, 1.0E-6D);
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
        }
    }

    @Test
    void compliantGuideSettlesBeforeRigidCaptureWithoutLongitudinalDrive() throws Exception{
        WaypointSpline route = WaypointSpline.of(List.of(Vec3.ZERO,
                new Vec3(20.0D, 0.0D, 0.0D)));
        try(Fixture ctx = new Fixture(new Vec3(5.0D, 0.0D, 0.5D));
            SableSplineConstraint constraint = new SableSplineConstraint()){
            WaypointSpline.Projection projection = route.project(ctx.position());
            var capture = new SableSplineConstraint.CaptureSettings(
                    0.75D, 0.05D, Math.toRadians(8.0D), 0.75D,
                    18.0D, 8.0D, 8.0D, 1.25D);
            assertTrue(constraint.update(ctx.body, route, projection,
                    Vec3.ZERO, FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    capture, 2.0D, (pos, dir, step) -> true));
            constraint.step(0.01D);
            assertEquals(SableSplineConstraint.Stage.GUIDING, constraint.stage());
            double maximumSpeed = 0.0D;
            for(int idx = 0; idx < 400 && !constraint.rigid(); idx++){
                invoke("step", ctx.scene, 0.01D);
                maximumSpeed = Math.max(maximumSpeed, ctx.speed());
                constraint.step(0.01D);
            }
            assertTrue(constraint.rigid(), "stage " + constraint.stage()
                    + " position " + ctx.position() + " speed " + ctx.speed()
                    + " maximum speed " + maximumSpeed);
            assertTrue(route.project(ctx.position()).distance() < 0.055D);
            assertTrue(maximumSpeed < 3.0D);
            assertFalse(ctx.speed() > 0.80D);
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
        }
    }

    @Test
    void hazardDetachAndRecapturePreserveHighSpeedBodyState() throws Exception{
        AtomicBoolean clear = new AtomicBoolean(true);
        WaypointSpline route = CURVE;
        try(Fixture ctx = new Fixture(); SableSplineConstraint constraint = new SableSplineConstraint()){
            WaypointSpline.Projection initial = route.project(Vec3.ZERO);
            assertTrue(SplineConstraintFrame.beforePredictedHandoff(route, initial,
                    Vec3.ZERO, SplineConstraintFrame.AxisPolicy.HORIZONTAL, 0.01D, 2.0D));
            assertTrue(SplineConstraintFrame.canCapture(Vec3.ZERO, Vec3.ZERO, FORWARD, UP,
                    initial, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    0.05D, Math.toRadians(3.0D), 0.15D));
            assertTrue(constraint.update(ctx.body, route, initial,
                    Vec3.ZERO, FORWARD, UP, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    0.05D, Math.toRadians(3.0D), 0.15D, 2.0D,
                    (pos, dir, step) -> clear.get()));
            constraint.step(0.01D);
            assertTrue(constraint.active());
            ctx.setVelocity(new Vec3(60.0D, 0.0D, 0.0D));
            clear.set(false);
            constraint.step(0.01D);
            assertTrue(!constraint.active());
            assertEquals(60.0D, ctx.speed(), 1.0E-6D);
            Vec3 position = ctx.position();
            WaypointSpline.Projection recapture = route.project(position);
            Vec3 recaptureDirection = recapture.tangent().normalize();
            Vec3 localRecaptureDirection = ctx.localDirection(recaptureDirection);
            assertTrue(SplineConstraintFrame.canCapture(position,
                    recaptureDirection.scale(60.0D), recaptureDirection, UP,
                    recapture, SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    0.05D, Math.toRadians(3.0D), 0.15D));
            clear.set(true);
            assertTrue(constraint.update(ctx.body, route, recapture,
                    Vec3.ZERO, localRecaptureDirection, UP,
                    SplineConstraintFrame.AxisPolicy.HORIZONTAL,
                    0.05D, Math.toRadians(3.0D), 0.15D, 2.0D,
                    (pos, dir, step) -> clear.get()));
            constraint.step(0.01D);
            assertTrue(constraint.active());
            verify(ctx.pipeline, org.mockito.Mockito.times(2))
                    .addConstraint(isNull(), same(ctx.body), any(GenericConstraintConfiguration.class));
            invoke("step", ctx.scene, 0.01D);
            assertTrue(ctx.speed() <= 60.0D && ctx.speed() > 59.9D);
            verify(ctx.pipeline, never()).remove(ctx.body);
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
            verify(ctx.pipeline, never()).resetVelocity(any());
            verify(ctx.rigidBody, never()).addLinearAndAngularVelocity(any(), any());
        }
    }

    private static Object invoke(String name, Object... args) throws Exception{
        return METHODS.get(name).invoke(null, args);
    }

    // Bridge a loaded sublevel to an isolated zero-gravity native scene
    private static final class Fixture implements AutoCloseable{
        private final ServerLevel level = mock(ServerLevel.class);
        private final ServerSubLevel body = mock(ServerSubLevel.class);
        private final SubLevelPhysicsSystem system = mock(SubLevelPhysicsSystem.class);
        private final PhysicsPipeline pipeline = mock(PhysicsPipeline.class);
        private final RigidBodyHandle rigidBody = mock(RigidBodyHandle.class);
        private final GenericConstraintHandle handle = mock(GenericConstraintHandle.class);
        private final MassData mass = mock(MassData.class);
        private final MockedStatic<SubLevelPhysicsSystem> systems = mockStatic(SubLevelPhysicsSystem.class);
        private final long scene;
        private long joint;
        private boolean removed;

        private Fixture() throws Exception{
            this(Vec3.ZERO);
        }

        private Fixture(Vec3 position) throws Exception{
            scene = (long) invoke("initialize", 0.0D, 0.0D, 0.0D, 0.0D);
            invoke("createBox", scene, 1, 0.5D, 0.5D, 0.5D, 100.0D,
                    new double[]{position.x, position.y, position.z,
                            0.0D, 0.0D, 0.0D, 1.0D});
            when(body.getLevel()).thenReturn(level);
            when(body.logicalPose()).thenReturn(new Pose3d());
            when(body.getMassTracker()).thenReturn(mass);
            when(mass.getMass()).thenReturn(100.0D);
            when(system.getPipeline()).thenReturn(pipeline);
            when(system.getPhysicsHandle(body)).thenReturn(rigidBody);
            when(rigidBody.isValid()).thenReturn(true);
            when(handle.isValid()).thenAnswer(call -> !removed);
            systems.when(() -> SubLevelPhysicsSystem.get(level)).thenReturn(system);
            when(pipeline.readPose(same(body), any(Pose3d.class))).thenAnswer(call -> {
                double[] res = new double[7];
                invoke("getPose", scene, 1, res);
                Pose3d pose = call.getArgument(1);
                pose.position().set(res[0], res[1], res[2]);
                pose.orientation().set(res[3], res[4], res[5], res[6]);
                return pose;
            });
            when(rigidBody.getLinearVelocity(any(Vector3d.class))).thenAnswer(call -> {
                double[] res = new double[3];
                invoke("getLinearVelocity", scene, 1, res);
                return ((Vector3d) call.getArgument(0)).set(res);
            });
            doAnswer(call -> {
                Vector3dc val = call.getArgument(0);
                invoke("addLinearAngularVelocities", scene, 1, val.x(), val.y(), val.z(),
                        0.0D, 0.0D, 0.0D, true);
                return null;
            }).when(rigidBody).addLinearAndAngularVelocity(any(), any());
            when(pipeline.addConstraint(isNull(), same(body), any(GenericConstraintConfiguration.class)))
                    .thenAnswer(call -> {
                        GenericConstraintConfiguration config = call.getArgument(2);
                        Vector3dc first = config.pos1();
                        Vector3dc second = config.pos2();
                        Quaterniondc firstRotation = config.orientation1();
                        Quaterniondc secondRotation = config.orientation2();
                        int mask = config.lockedAxes().stream().mapToInt(axis -> 1 << axis.ordinal()).sum();
                        joint = (long) invoke("addGenericConstraint", scene, -1, 1,
                                first.x(), first.y(), first.z(),
                                firstRotation.x(), firstRotation.y(), firstRotation.z(), firstRotation.w(),
                                second.x(), second.y(), second.z(),
                                secondRotation.x(), secondRotation.y(), secondRotation.z(), secondRotation.w(), mask);
                        removed = false;
                        return handle;
                    });
            doAnswer(call -> {
                Vector3dc pos = call.getArgument(0);
                Quaterniondc rotation = call.getArgument(1);
                invoke("setConstraintFrame", scene, joint, 0, pos.x(), pos.y(), pos.z(),
                        rotation.x(), rotation.y(), rotation.z(), rotation.w());
                return null;
            }).when(handle).setFrame1(any(), any());
            doAnswer(call -> {
                ConstraintJointAxis axis = call.getArgument(0);
                invoke("setConstraintMotor", scene, joint, axis.ordinal(),
                        call.getArgument(1), call.getArgument(2), call.getArgument(3),
                        call.getArgument(4), call.getArgument(5));
                return null;
            }).when(handle).setMotor(any(), anyDouble(), anyDouble(),
                    anyDouble(), anyBoolean(), anyDouble());
            doAnswer(call -> {
                invoke("removeConstraint", scene, joint);
                removed = true;
                return null;
            }).when(handle).remove();
        }

        private Vec3 position() throws Exception{
            double[] res = new double[7];
            invoke("getPose", scene, 1, res);
            return new Vec3(res[0], res[1], res[2]);
        }

        private double speed() throws Exception{
            return velocity().length();
        }

        // Transform one world direction into the current native body frame
        private Vec3 localDirection(Vec3 value) throws Exception{
            double[] res = new double[7];
            invoke("getPose", scene, 1, res);
            Vector3d local = new Quaterniond(res[3], res[4], res[5], res[6])
                    .transformInverse(new Vector3d(value.x, value.y, value.z));
            return new Vec3(local.x, local.y, local.z);
        }

        // Set native velocity as an external vehicle actuator would
        private void setVelocity(Vec3 value) throws Exception{
            invoke("addLinearAngularVelocities", scene, 1,
                    value.x, value.y, value.z, 0.0D, 0.0D, 0.0D, true);
        }

        // Represent vehicle-block propulsion outside the production route constraint
        private void applyDrive(Vec3 direction, double targetSpeed, double timeStep) throws Exception{
            Vec3 dir = direction.normalize();
            Vector3d current = velocity();
            double error = targetSpeed - current.dot(dir.x, dir.y, dir.z);
            double maximumChange = 400.0D * timeStep;
            double change = Math.max(-maximumChange, Math.min(maximumChange, error));
            invoke("addLinearAngularVelocities", scene, 1,
                    dir.x * change, dir.y * change, dir.z * change,
                    0.0D, 0.0D, 0.0D, true);
        }

        private Vector3d velocity() throws Exception{
            double[] res = new double[3];
            invoke("getLinearVelocity", scene, 1, res);
            return new Vector3d(res);
        }

        @Override
        public void close() throws Exception{
            systems.close();
            invoke("dispose", scene);
        }
    }
}
