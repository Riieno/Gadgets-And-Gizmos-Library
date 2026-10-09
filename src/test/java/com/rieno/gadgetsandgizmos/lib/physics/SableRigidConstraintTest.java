package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Measure peak error against the native solver while using the production attachment API
class SableRigidConstraintTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final double TIME_STEP = 1.0D / 60.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @BeforeAll static void bootstrap() throws Exception{
        SableSplineConstraintTest.bootstrap();
        for(Method method : Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D").getDeclaredMethods()){
            method.setAccessible(true);
            METHODS.put(method.getName(), method);
        }
    }

    @Test void heavyWorldMountedHeadRemainsExactDuringRapidRotation() throws Exception{
        try(Fixture ctx = new Fixture()){
            var child = ctx.body(100000.0D, new Vector3d(4.0D, 0.0D, 0.0D));
            var childAnchor = new Vector3d(child.logicalPose().rotationPoint()).add(-4.0D, 0.0D, 0.0D);
            var joint = ctx.attach(null, child, new Vector3d(), childAnchor, new Quaterniond());
            assertInstanceOf(FixedConstraintHandle.class, joint);
            double peakOffset = 0.0D;
            for(int idx = 0; idx < 240; idx++){
                Quaterniond target = new Quaterniond().rotateZ(Math.sin(idx * TIME_STEP * Math.PI * 4.0D) * Math.PI / 3.0D);
                SableConstraintApi.setFrame(joint, 1, new Vector3d(), target);
                ctx.force(child);
                ctx.step();
                peakOffset = Math.max(peakOffset, ctx.anchor(child, childAnchor).length());
                assertTrue(peakOffset < 2.0E-6D, "Peak anchor displacement " + peakOffset);
                assertTrue(rotationError(target, child.logicalPose().orientation()) < 1.0E-6D);
                Vector3d rotation = ctx.velocity(child, true);
                assertTrue(rotation.distance(joint.relativeAngularVelocity) < 2.0E-5D);
                Vector3d expectedVelocity = new Vector3d(rotation).cross(child.logicalPose().position());
                assertTrue(ctx.velocity(child, false).distance(expectedVelocity) < 2.0E-5D);
            }
        }
    }

    @Test void heavyNestedAndSiblingHeadsPreserveTheMovingParentsMomentum() throws Exception{
        try(Fixture ctx = new Fixture()){
            var root = ctx.body(1000.0D, new Vector3d());
            var child = ctx.body(100000.0D, new Vector3d(4.0D, 0.0D, 0.0D));
            var nested = ctx.body(20000.0D, new Vector3d(8.0D, 0.0D, 0.0D));
            var sibling = ctx.body(15000.0D, new Vector3d(0.0D, 4.0D, 0.0D));
            var first = ctx.attach(root, child, ctx.local(root, 0.0D, 0.0D), ctx.local(child, -4.0D, 0.0D), new Quaterniond());
            var second = ctx.attach(child, nested, ctx.local(child, 0.0D, 0.0D), ctx.local(nested, -4.0D, 0.0D), new Quaterniond());
            var third = ctx.attach(root, sibling, ctx.local(root, 0.0D, 0.0D), ctx.local(sibling, 0.0D, -4.0D), new Quaterniond());
            for(int idx = 0; idx < 120; idx++){
                first.setFrame(1, first.parentAnchor(), new Quaterniond().rotateZ(Math.sin(idx * 0.2D) * 0.7D));
                second.setFrame(1, second.parentAnchor(), new Quaterniond().rotateY(Math.sin(idx * 0.3D) * 0.5D));
                third.setFrame(1, third.parentAnchor(), new Quaterniond().rotateX(Math.sin(idx * 0.4D) * 0.6D));
                ctx.force(nested);
                ctx.solve();
                var before = ctx.momentum();
                ctx.project();
                var after = ctx.momentum();
                assertTrue(before.center.distance(after.center) < 1.0E-5D);
                assertTrue(before.linear.distance(after.linear) < Math.max(0.1D, before.linear.length() * 2.0E-6D));
                assertTrue(before.angular.distance(after.angular) < Math.max(0.2D, before.angular.length() * 2.0E-6D));
                for(var joint : List.of(first, second, third)){
                    assertTrue(ctx.anchor(joint.parent(), joint.parentAnchor()).distance(ctx.anchor(joint.child(), joint.childAnchor())) < 2.0E-5D);
                    Quaterniond expected = new Quaterniond(joint.parent().logicalPose().orientation()).mul(joint.relativeOrientation());
                    assertTrue(rotationError(expected, joint.child().logicalPose().orientation()) < 1.0E-6D);
                }
            }
            assertTrue(ctx.velocity(root, false).length() > 1.0D, "Load forces must reach the moving parent");
        }
    }

    @Test void reversedWorldAttachmentRetainsControlledAnchorMotion() throws Exception{
        try(Fixture ctx = new Fixture()){
            var child = ctx.body(100000.0D, new Vector3d(4.0D, 0.0D, 0.0D));
            Vector3d anchor = ctx.local(child, -4.0D, 0.0D);
            var joint = ctx.attach(child, null, anchor, new Vector3d(), new Quaterniond());
            for(int idx = 0; idx < 60; idx++){
                var worldAnchor = new Vector3d(idx * 0.02D, 0.0D, 0.0D);
                var target = new Quaterniond().rotateY(idx * 0.01D);
                joint.setFrame(2, worldAnchor, target);
                ctx.force(child);
                ctx.step();
                assertTrue(ctx.anchor(child, anchor).distance(worldAnchor) < 2.0E-6D);
                assertTrue(rotationError(target, child.logicalPose().orientation()) < 1.0E-6D);
                if(idx > 0){
                    Vector3d anchorVelocity = ctx.velocity(child, true).cross(new Vector3d(ctx.anchor(child, anchor))
                            .sub(child.logicalPose().position())).add(ctx.velocity(child, false));
                    assertTrue(anchorVelocity.distance(new Vector3d(1.2D, 0.0D, 0.0D)) < 2.0E-5D);
                }
            }
        }
    }

    @Test void duplicateParentsCyclesAndReleasedJointsCannotKeepMovingBodies() throws Exception{
        try(Fixture ctx = new Fixture()){
            var first = ctx.body(1000.0D, new Vector3d());
            var second = ctx.body(100000.0D, new Vector3d(4.0D, 0.0D, 0.0D));
            var joint = ctx.attach(first, second, ctx.local(first, 0.0D, 0.0D), ctx.local(second, -4.0D, 0.0D), new Quaterniond());
            assertNull(SableConstraintApi.rigidFixedConstraint(ctx.level, null, second, new Vector3d(), ctx.local(second, 0.0D, 0.0D), new Quaterniond()));
            assertNull(SableConstraintApi.rigidFixedConstraint(ctx.level, second, first, ctx.local(second, 0.0D, 0.0D), ctx.local(first, 0.0D, 0.0D), new Quaterniond()));
            assertThrows(IllegalArgumentException.class, () -> joint.setFrame(1, new Vector3d(Double.NaN), new Quaterniond()));
            joint.remove();
            joint.remove();
            assertFalse(joint.isValid());
            clearInvocations(ctx.pipeline);
            ctx.step();
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
            var replacement = ctx.attach(null, second, new Vector3d(), ctx.local(second, 0.0D, 0.0D), new Quaterniond());
            when(second.isRemoved()).thenReturn(true);
            ctx.step();
            assertFalse(replacement.isValid());
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    @Test void levelUnloadReleasesNativeJointsBeforeTheSceneDisposes() throws Exception{
        try(Fixture ctx = new Fixture()){
            var body = ctx.body(100000.0D, new Vector3d(4.0D, 0.0D, 0.0D));
            var joint = ctx.attach(null, body, new Vector3d(), ctx.local(body, -4.0D, 0.0D), new Quaterniond());
            SableRigidConstraintEvents.levelUnloaded(new LevelEvent.Unload(ctx.level));
            assertFalse(joint.isValid());
            assertThrows(IllegalStateException.class, () -> joint.setFrame(1, new Vector3d(), new Quaterniond()));
            ctx.step();
            verify(ctx.pipeline, never()).teleport(any(), any(), any());
        }
    }

    // Compare orientations without depending on quaternion signs
    private static double rotationError(Quaterniondc first, Quaterniondc second){
        Quaterniond delta = new Quaterniond(first).normalize().conjugate().mul(new Quaterniond(second).normalize());
        return 2.0D * Math.atan2(Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z), Math.abs(delta.w));
    }

    // Invoke the exact installed native implementation
    private static Object invoke(String name, Object... args) throws Exception{ return METHODS.get(name).invoke(null, args); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private record Momentum(Vector3d center, Vector3d linear, Vector3d angular){}

    // Bridge mocked game ownership to real Rapier bodies, joints, poses, and velocities
    private static final class Fixture implements AutoCloseable{
        final ServerLevel level = mock(ServerLevel.class);
        final SubLevelPhysicsSystem system = mock(SubLevelPhysicsSystem.class);
        final PhysicsPipeline pipeline = mock(PhysicsPipeline.class);
        final Map<ServerSubLevel, Integer> bodies = new IdentityHashMap<>();
        final List<SableRigidConstraint> joints = new ArrayList<>();
        final MockedStatic<SubLevelContainer> containers = mockStatic(SubLevelContainer.class);
        final long scene;

        Fixture() throws Exception{
            scene = (long)invoke("initialize", 0.0D, -9.81D, 0.0D, 0.0D);
            var container = mock(ServerSubLevelContainer.class);
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            when(container.physicsSystem()).thenReturn(system);
            when(system.getPipeline()).thenReturn(pipeline);
            when(system.getLevel()).thenReturn(level);
            when(pipeline.addConstraint(any(), any(), any(FixedConstraintConfiguration.class))).thenAnswer(call -> {
                FixedConstraintConfiguration config = call.getArgument(2);
                Vector3dc first = config.pos1();
                Vector3dc second = config.pos2();
                Quaterniondc rotation = config.orientation();
                long joint = (long)invoke("addFixedConstraint", scene,
                        bodies.getOrDefault(call.getArgument(0), -1), bodies.getOrDefault(call.getArgument(1), -1),
                        first.x(), first.y(), first.z(), second.x(), second.y(), second.z(),
                        rotation.x(), rotation.y(), rotation.z(), rotation.w());
                return Class.forName("dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle")
                        .getConstructor(long.class, long.class).newInstance(scene, joint);
            });
            when(pipeline.getLinearVelocity(any(), any())).thenAnswer(call -> ((Vector3d)call.getArgument(1)).set(velocity(call.getArgument(0), false)));
            when(pipeline.getAngularVelocity(any(), any())).thenAnswer(call -> ((Vector3d)call.getArgument(1)).set(velocity(call.getArgument(0), true)));
            doAnswer(call -> {
                Vector3dc pos = call.getArgument(1);
                Quaterniondc rotation = call.getArgument(2);
                invoke("teleportObject", scene, bodies.get(call.getArgument(0)), pos.x(), pos.y(), pos.z(),
                        rotation.x(), rotation.y(), rotation.z(), rotation.w());
                return null;
            }).when(pipeline).teleport(any(), any(), any());
            doAnswer(call -> {
                Vector3dc linear = call.getArgument(1);
                Vector3dc angular = call.getArgument(2);
                invoke("addLinearAngularVelocities", scene, bodies.get(call.getArgument(0)),
                        linear.x(), linear.y(), linear.z(), angular.x(), angular.y(), angular.z(), true);
                return null;
            }).when(pipeline).addLinearAndAngularVelocity(any(), any(), any());
            doAnswer(call -> { sync(call.getArgument(0)); return null; }).when(system).updatePose(any());
        }

        ServerSubLevel body(double mass, Vector3d pos) throws Exception{
            int idx = bodies.size();
            var body = mock(ServerSubLevel.class);
            var data = mock(MassData.class);
            var pose = new Pose3d();
            pose.position().set(pos);
            pose.rotationPoint().set(20481032.5D + idx * 512.0D, 128.5D, 20481032.5D);
            when(body.getLevel()).thenReturn(level);
            when(body.logicalPose()).thenReturn(pose);
            when(body.getMassTracker()).thenReturn(data);
            when(data.getMass()).thenReturn(mass);
            when(data.getCenterOfMass()).thenReturn(pose.rotationPoint());
            when(data.getInertiaTensor()).thenReturn(new Matrix3d().scale(mass * 10.0D));
            invoke("createSubLevel", scene, idx, new double[]{pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D, 1.0D});
            Vector3d center = pose.rotationPoint();
            invoke("setCenterOfMass", scene, idx, center.x, center.y, center.z);
            invoke("setLocalBounds", scene, idx, (int)center.x - 1, 127, (int)center.z - 1, (int)center.x + 2, 130, (int)center.z + 2);
            invoke("setMassProperties", scene, idx, mass, new double[3], new double[]{mass * 10.0D, 0.0D, 0.0D, 0.0D, mass * 10.0D, 0.0D, 0.0D, 0.0D, mass * 10.0D});
            bodies.put(body, idx);
            return body;
        }

        SableRigidConstraint attach(ServerSubLevel parent, ServerSubLevel child, Vector3dc first, Vector3dc second, Quaterniondc rotation) throws Exception{
            var joint = SableConstraintApi.rigidFixedConstraint(level, parent, child, first, second, rotation);
            assertNotNull(joint);
            joints.add(joint);
            return joint;
        }

        Vector3d local(ServerSubLevel body, double x, double y){ return new Vector3d(body.logicalPose().rotationPoint()).add(x, y, 0.0D); }

        Vector3d anchor(ServerSubLevel body, Vector3dc local){
            return body.logicalPose().orientation().transform(new Vector3d(local).sub(body.logicalPose().rotationPoint())).add(body.logicalPose().position());
        }

        Vector3d velocity(ServerSubLevel body, boolean angular) throws Exception{
            double[] res = new double[3];
            invoke(angular ? "getAngularVelocity" : "getLinearVelocity", scene, bodies.get(body), res);
            return new Vector3d(res);
        }

        void force(ServerSubLevel body) throws Exception{
            invoke("applyForceAndTorque", scene, bodies.get(body), 200000.0D, -1000000.0D, 100000.0D,
                    500000.0D, 200000.0D, 300000.0D, true);
        }

        void sync(ServerSubLevel body) throws Exception{
            double[] res = new double[7];
            invoke("getPose", scene, bodies.get(body), res);
            body.logicalPose().position().set(res[0], res[1], res[2]);
            body.logicalPose().orientation().set(res[3], res[4], res[5], res[6]);
        }

        void solve() throws Exception{
            SableRigidConstraintEvents.prePhysicsTick(new ForgeSablePrePhysicsTickEvent(system, TIME_STEP));
            invoke("step", scene, TIME_STEP);
            for(var body : bodies.keySet()) sync(body);
        }

        void project(){ SableRigidConstraintEvents.postPhysicsTick(new ForgeSablePostPhysicsTickEvent(system, TIME_STEP)); }
        void step() throws Exception{ solve(); project(); }

        Momentum momentum() throws Exception{
            double mass = 0.0D;
            Vector3d center = new Vector3d();
            Vector3d linear = new Vector3d();
            Vector3d angular = new Vector3d();
            for(var body : bodies.keySet()){
                double val = body.getMassTracker().getMass();
                mass += val;
                center.fma(val, body.logicalPose().position());
                linear.fma(val, velocity(body, false));
            }
            center.div(mass);
            for(var body : bodies.keySet()){
                var rotation = new Matrix3d().rotation(body.logicalPose().orientation());
                var inertia = new Matrix3d(rotation).mul(body.getMassTracker().getInertiaTensor()).mul(new Matrix3d(rotation).transpose());
                angular.add(inertia.transform(velocity(body, true)));
                angular.add(new Vector3d(body.logicalPose().position()).sub(center).cross(velocity(body, false).mul(body.getMassTracker().getMass())));
            }
            return new Momentum(center, linear, angular);
        }

        @Override public void close() throws Exception{
            for(var joint : joints) joint.remove();
            containers.close();
            invoke("dispose", scene);
        }
    }
}
