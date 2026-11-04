package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

// Verify joint lifetime without requiring a native physics scene in unit tests
class SableFrameConstraintTest{
    // Stationary targets must let bodies sleep and avoid repeated native frame writes
    @Test void unchangedFramesDoNotWakeOrRetargetBodies(){
        var fixture = new Fixture();
        try(var joint = new SableFrameConstraint()){
            assertTrue(fixture.update(joint));
            assertEquals(1, fixture.created);
            assertEquals(1, fixture.wakes);
            for(int idx = 0; idx < 100; idx++) assertTrue(fixture.update(joint));
            assertEquals(1, fixture.created);
            assertEquals(1, fixture.wakes);
            assertEquals(0, fixture.frames);
            assertTrue(joint.update(fixture.pipeline, null, fixture.child, new Vector3d(1, 0, 0),
                    new Vector3d(), new Quaterniond()));
            assertEquals(1, fixture.frames);
            assertEquals(2, fixture.wakes);
        }
    }

    // Body removal must release all ownership before native body destruction
    @Test void unloadAndReloadCannotReuseAnOldNativeHandle(){
        var fixture = new Fixture();
        try(var joint = new SableFrameConstraint()){
            fixture.update(joint);
            SableFrameConstraint.releaseBody(fixture.pipeline, fixture.child);
            assertEquals(1, fixture.removed);
            assertFalse(joint.isValid());
            fixture.disposed = true;
            joint.close();
            assertFalse(joint.isValid());
            fixture.disposed = false;
            fixture.update(joint);
            assertEquals(2, fixture.created);
            SableFrameConstraint.releasePipeline(fixture.pipeline);
            assertEquals(2, fixture.removed);
            fixture.disposed = true;
        }
    }

    // Supply handles which fail immediately if touched after their native scene disappears
    private static final class Fixture{
        private int created, removed, wakes, frames;
        private boolean valid, disposed;
        private final PhysicsPipelineBody child = (PhysicsPipelineBody)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PhysicsPipelineBody.class},
                (proxy, method, args) -> method.getName().equals("isRemoved") ? false : null);
        private final GenericConstraintHandle handle = (GenericConstraintHandle)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{GenericConstraintHandle.class}, (proxy, method, args) -> {
                    if(disposed) throw new AssertionError("A disposed native scene was accessed");
                    return switch(method.getName()){
                        case "isValid" -> valid;
                        case "remove" -> { valid = false; removed++; yield null; }
                        case "setFrame1", "setFrame2" -> { frames++; yield null; }
                        default -> null;
                    };
                });
        private final PhysicsPipeline pipeline = (PhysicsPipeline)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PhysicsPipeline.class}, (proxy, method, args) -> {
                    if(method.getName().equals("addConstraint")){ created++; valid = true; return handle; }
                    if(method.getName().equals("wakeUp")) wakes++;
                    return null;
                });

        // Update an unchanged attachment with fresh value objects
        private boolean update(SableFrameConstraint joint){
            return joint.update(pipeline, null, child, new Vector3d(), new Vector3d(), new Quaterniond());
        }
    }
}
