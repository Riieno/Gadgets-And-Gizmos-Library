package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class SableFixedConstraintTest{
    @Test void fixedConfigurationAndRotaryFramesPreserveTheRequestedPose() throws Exception{
        Quaterniond base = new Quaterniond().rotateX(Math.PI / 2.0D);
        Quaterniond mounted = new Quaterniond().rotateZ(Math.PI / 2.0D);
        Quaterniond relative = SableConstraintApi.rotaryOrientation(base, mounted, Math.PI / 3.0D);
        Quaterniond local = new Quaterniond(base).conjugate().mul(relative).mul(mounted);
        assertEquals(1.0D, Math.abs(local.dot(new Quaterniond().rotateY(Math.PI / 3.0D))), 1.0E-10D);
        Object config = SableConstraintApi.fixedConfiguration(new Vector3d(), new Vector3d(4.0D, 0.0D, 0.0D), relative);
        assertInstanceOf(FixedConstraintConfiguration.class, config);
    }

    @Test void nativeFixedJointHoldsAHeavyOffsetLoadWhileItsFramesRotate() throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        Class<?> rapier = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
        Method init = method(rapier, "initialize", double.class, double.class, double.class, double.class);
        long scene = (long)init.invoke(null, 0.0D, -9.81D, 0.0D, 0.0D);
        try{
            method(rapier, "createSubLevel", long.class, int.class, double[].class)
                    .invoke(null, scene, 0, new double[]{4.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D});
            method(rapier, "setCenterOfMass", long.class, int.class, double.class, double.class, double.class)
                    .invoke(null, scene, 0, 0.0D, 0.0D, 0.0D);
            method(rapier, "setLocalBounds", long.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(null, scene, 0, -1, -1, -1, 1, 1, 1);
            method(rapier, "setMassProperties", long.class, int.class, double.class, double[].class, double[].class)
                    .invoke(null, scene, 0, 100000.0D, new double[3],
                            new double[]{1000000.0D, 0.0D, 0.0D, 0.0D, 1000000.0D, 0.0D, 0.0D, 0.0D, 1000000.0D});
            Method add = method(rapier, "addFixedConstraint", long.class, int.class, int.class,
                    double.class, double.class, double.class, double.class, double.class, double.class,
                    double.class, double.class, double.class, double.class);
            long joint = (long)add.invoke(null, scene, -1, 0, 0.0D, 0.0D, 0.0D, -4.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D, 1.0D);
            var handle = (PhysicsConstraintHandle)Class.forName(
                    "dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle")
                    .getConstructor(long.class, long.class).newInstance(scene, joint);
            handle.setContactsEnabled(false);
            Quaterniond target = new Quaterniond().rotateZ(Math.PI / 6.0D);
            SableConstraintApi.setFrame(handle, 1, new Vector3d(), target);
            SableConstraintApi.setFrame(handle, 2, new Vector3d(-4.0D, 0.0D, 0.0D), new Quaterniond());
            Method step = method(rapier, "step", long.class, double.class);
            Method force = method(rapier, "applyForceAndTorque", long.class, int.class,
                    double.class, double.class, double.class, double.class, double.class, double.class, boolean.class);
            for(int idx = 0; idx < 180; idx++){
                force.invoke(null, scene, 0, 200000.0D, -1000000.0D, 100000.0D, 500000.0D, 200000.0D, 300000.0D, true);
                step.invoke(null, scene, 1.0D / 60.0D);
            }
            double[] pose = new double[7];
            method(rapier, "getPose", long.class, int.class, double[].class).invoke(null, scene, 0, pose);
            Vector3d expected = target.transform(new Vector3d(4.0D, 0.0D, 0.0D));
            assertEquals(expected.x, pose[0], 0.005D);
            assertEquals(expected.y, pose[1], 0.005D);
            assertEquals(expected.z, pose[2], 0.005D);
            assertEquals(1.0D, Math.abs(target.dot(new Quaterniond(pose[3], pose[4], pose[5], pose[6]))), 0.0001D);
            handle.remove();
            assertFalse(handle.isValid());
        }finally{
            method(rapier, "dispose", long.class).invoke(null, scene);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception{
        Method res = type.getDeclaredMethod(name, args);
        res.setAccessible(true);
        return res;
    }
}
