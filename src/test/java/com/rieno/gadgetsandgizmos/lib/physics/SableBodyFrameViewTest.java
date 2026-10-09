package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.Direction;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Ensure a virtual craft frame changes axes and inertia while preserving physical block origins
class SableBodyFrameViewTest{
    @Test
    void craftOrientationComposesWithShipRotationWithoutRotatingBlockPositions(){
        var shipRotation = new Quaterniond().rotateY(.4).rotateX(.2);
        var pose = new Pose3d(new Vector3d(12, 20, 30), shipRotation, new Vector3d(1, 2, 3), new Vector3d(1));
        var frame = new ScmOrientation(Direction.UP, Direction.SOUTH);
        var view = new SableBodyFrameView.FramePose(pose, frame);
        var expected = shipRotation.transform(new Vector3d(0, 1, 0));
        assertEquals(0, view.orientation().transform(new Vector3d(0, 0, -1), new Vector3d()).distance(expected), 1.0E-9);
        var point = new Vector3d(100, 10, 200);
        var dest = new Vector3d();
        view.transformPosition(point, dest);
        assertEquals(0, dest.distance(pose.transformPosition(new Vector3d(point))), 1.0E-9);
    }

    @Test
    void bodyInertiaUsesTheSameSignedAxesAsTheControlFrame(){
        var mass = mock(MassData.class);
        when(mass.getMass()).thenReturn(20D);
        when(mass.getInertiaTensor()).thenReturn(new Matrix3d().scaling(2, 3, 5));
        var view = new SableBodyFrameView.FrameMass(mass, new ScmOrientation(Direction.UP, Direction.SOUTH));
        assertEquals(20D, view.getMass());
        assertEquals(2D, view.getInertiaTensor().m00(), 1.0E-9);
        assertEquals(5D, view.getInertiaTensor().m11(), 1.0E-9);
        assertEquals(3D, view.getInertiaTensor().m22(), 1.0E-9);
    }
}
