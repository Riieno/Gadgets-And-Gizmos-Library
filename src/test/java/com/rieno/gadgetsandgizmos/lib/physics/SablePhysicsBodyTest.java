package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

// Model native unload before Java removal without calling the native backend
class SablePhysicsBodyTest{
    @Test
    void nativeMembershipOverridesTheJavaRemovedFlag() throws Exception{
        var fixture = new Fixture();
        assertTrue(SablePhysicsBodyApi.isLoaded(fixture.pipeline, fixture.body));
        SableConstraintApi.wakeUp(fixture.pipeline, fixture.body);
        assertEquals(1, fixture.wakes);
        fixture.loaded = false;
        assertFalse(fixture.body.isRemoved());
        assertFalse(SablePhysicsBodyApi.isLoaded(fixture.pipeline, fixture.body));
        SableConstraintApi.wakeUp(fixture.pipeline, fixture.body);
        assertEquals(1, fixture.wakes);
        fixture.loaded = true;
        SableConstraintApi.wakeUp(fixture.pipeline, fixture.body);
        assertEquals(2, fixture.wakes);
    }

    @Test
    void constraintsNeverReachAnUnloadedBody() throws Exception{
        var fixture = new Fixture();
        fixture.loaded = false;
        assertNull(SableConstraintApi.addConstraint(fixture.pipeline, null, fixture.body,
                SableConstraintApi.fixedConfiguration(new Vector3d(), new Vector3d(), new Quaterniond())));
        assertNull(SableConstraintApi.fixedFrameConstraint(fixture.pipeline, null, fixture.body,
                new Vector3d(), new Vector3d(), new Quaterniond()));
        assertNull(SableConstraintApi.rotaryConstraint(fixture.pipeline, null, fixture.body,
                new Vector3d(), new Vector3d(), new Vector3d(0, 1, 0), new Vector3d(0, 1, 0)));
        assertEquals(0, fixture.constraints);
        fixture.loaded = true;
        SableConstraintApi.fixedFrameConstraint(fixture.pipeline, null, fixture.body,
                new Vector3d(), new Vector3d(), new Quaterniond());
        assertEquals(1, fixture.constraints);
    }

    @Test
    void removedAndMissingBodiesAreUnavailable(){
        var fixture = new Fixture();
        assertFalse(SablePhysicsBodyApi.isLoaded(null, fixture.body));
        assertFalse(SablePhysicsBodyApi.isLoaded(fixture.pipeline, null));
        assertFalse(SablePhysicsBodyApi.isLoaded((dev.ryanhcode.sable.sublevel.ServerSubLevel)null));
        assertFalse(SablePhysicsBodyApi.isBlockEntityLoaded(null));
        fixture.removed = true;
        assertFalse(SablePhysicsBodyApi.isLoaded(fixture.pipeline, fixture.body));
    }

    private static final class Fixture{
        private boolean loaded = true, removed;
        private int wakes, constraints;
        private final PhysicsPipelineBody body = (PhysicsPipelineBody)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PhysicsPipelineBody.class},
                (proxy, method, args) -> method.getName().equals("isRemoved") ? removed : null);
        private final PhysicsPipeline pipeline = (PhysicsPipeline)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PhysicsPipeline.class, PhysicsBodyAvailability.class},
                (proxy, method, args) -> switch(method.getName()){
                    case "isBodyLoaded" -> loaded;
                    case "wakeUp" -> {
                        if(!loaded) throw new AssertionError("The removed native body was woken");
                        wakes++;
                        yield null;
                    }
                    case "addConstraint" -> { constraints++; yield null; }
                    default -> null;
                });
    }
}
