package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScmActuatorGeometryTest{
    @Test void tangentGimbalsMatchTheNativeCoordinatesForEveryMountAxis(){
        for(Vec3 facing : java.util.List.of(new Vec3(1,0,0), new Vec3(-1,0,0), new Vec3(0,1,0),
                new Vec3(0,-1,0), new Vec3(0,0,1), new Vec3(0,0,-1))){
            Vec3 ref = Math.abs(facing.y) > 0.999 ? new Vec3(0,0,1) : new Vec3(0,1,0);
            Vec3 right = facing.cross(ref).normalize();
            if(Math.abs(facing.y) > 0.999D) right = right.scale(-1.0D);
            Vec3 up = right.cross(facing).normalize();
            double tangent = Math.tan(Math.toRadians(30));
            Vec3 expected = facing.subtract(right.scale(0.75*tangent)).add(up.scale(0.5*tangent)).normalize();
            Vec3 actual = ScmActuatorGeometry.gimbalDirection(facing, 0.75, -0.5, 30);
            assertEquals(0, actual.distanceTo(expected), 1e-12);
            assertEquals(1, actual.length(), 1e-12);
            assertEquals(0, ScmActuatorGeometry.gimbalDirection(facing, 0, 0, 30).distanceTo(facing), 1e-12);
        }
    }

    @Test void aRotatedHostUsesItsOwnPivotAndPreservesForceCapacity(){
        var frame = new ScmGeometryFrame(new Vec3(10,20,30), new Vec3(0,0,-1), new Vec3(0,1,0), new Vec3(1,0,0));
        var rotation = ScmActuatorGeometry.rotaryDelta(new Vec3(0,1,0), 45, 135);
        assertEquals(0, ScmActuatorGeometry.position(frame, rotation, new Vec3(10,20,28))
                .distanceTo(new Vec3(8,20,30)), 1e-12);
        assertEquals(500, ScmActuatorGeometry.direction(frame, rotation, new Vec3(0,0,-500)).length(), 1e-10);
        assertEquals(0, ScmActuatorGeometry.position(frame, rotation, frame.origin()).distanceTo(frame.origin()), 1e-12);
    }

    @Test void tiltDeltaStartsFromTheCurrentPhysicalPose(){
        Vec3 current = ScmActuatorGeometry.gimbalDirection(new Vec3(0,1,0), 0.4, -0.3, 30);
        Vec3 target = ScmActuatorGeometry.gimbalDirection(new Vec3(0,1,0), -0.8, 0.5, 30);
        var identity = new ScmGeometryFrame(Vec3.ZERO, new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1));
        assertEquals(0, ScmActuatorGeometry.direction(identity, ScmActuatorGeometry.tiltDelta(current,target), current)
                .distanceTo(target), 1e-12);
    }
}
