package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify native controller evaluation cannot apply an impulse until SCM accepts its request
class SableImpulseCaptureTest{
    @Test
    void pointForcesAreCapturedAboutThePhysicalBodyCenter(){
        var handle = mock(RigidBodyHandle.class);
        var capture = new SableImpulseCapture(handle, new Vector3d(10, 20, 30));
        capture.applyImpulseAtPoint(new Vector3d(0, 2, 0), new Vector3d(12, 20, 30));
        capture.applyAngularImpulse(new Vector3d(0, 0, 1));
        var res = capture.snapshot(.5);
        assertEquals(new Vec3(0, 4, 0), res.force());
        assertEquals(new Vec3(0, 0, 10), res.torque());
        verifyNoInteractions(handle);
    }

    @Test
    void configuredImpulseFramePreservesPhysicalPositionsAndWorldVelocityReads(){
        var handle = mock(RigidBodyHandle.class);
        var capture = new SableImpulseCapture(handle, new Vector3d());
        var frame = new SableBodyFrameHandle(capture, new ScmOrientation(Direction.EAST, Direction.UP));
        frame.applyLinearAndAngularImpulse(new Vector3d(0, 0, -2), new Vector3d(0, 3, 0));
        var res = capture.snapshot(.5);
        assertEquals(0, res.force().distanceTo(new Vec3(4, 0, 0)), 1.0E-9);
        assertEquals(0, res.torque().distanceTo(new Vec3(0, 6, 0)), 1.0E-9);
        frame.getAngularVelocity();
        verify(handle).getAngularVelocity();
        verifyNoMoreInteractions(handle);
    }
}
