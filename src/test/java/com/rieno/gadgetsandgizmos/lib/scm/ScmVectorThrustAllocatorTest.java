package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScmVectorThrustAllocatorTest{
    @Test void separatedVectorEnginesYawWithoutNetStrafe(){
        var units = List.of(unit(new Vec3(-4, 0, 0)), unit(new Vec3(4, 0, 0)));
        for(double yaw : new double[]{-100, 100}){
            var res = ScmVectorThrustAllocator.allocate(units, new Vec3(0, 150, 0), new Vec3(0, yaw, 0), true);
            Vec3 force = res.forces().get(0).add(res.forces().get(1));
            Vec3 torque = units.get(0).momentArm().cross(res.forces().get(0))
                    .add(units.get(1).momentArm().cross(res.forces().get(1)));
            assertEquals(0, force.x, 0.01);
            assertEquals(0, force.z, 0.01);
            assertEquals(yaw, torque.y, 0.05);
            assertTrue(res.forces().get(0).z * res.forces().get(1).z < 0);
        }
    }

    @Test void onlyEnginesFacingRequestedStrafeDirectionFire(){
        var units = List.of(new ScmPrecisionAllocator.Unit(new Vec3(100, 0, 0), Vec3.ZERO, false),
                new ScmPrecisionAllocator.Unit(new Vec3(-100, 0, 0), Vec3.ZERO, false),
                new ScmPrecisionAllocator.Unit(new Vec3(0, 0, 100), Vec3.ZERO, false));
        for(double sign : new double[]{-1, 1}){
            var res = ScmVectorThrustAllocator.allocate(units, new Vec3(sign * 60, 0, 0), Vec3.ZERO, false);
            assertEquals(sign * 60, res.forces().stream().mapToDouble(v -> v.x).sum(), 1e-6);
            assertEquals(0, res.forces().get(sign > 0 ? 1 : 0).lengthSqr(), 1e-12);
            assertEquals(Vec3.ZERO, res.forces().get(2));
        }
    }

    @Test void gimbalCoordinatesRoundTripEveryNativeFacing(){
        for(Vec3 facing : List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),
                new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
            for(double x : new double[]{-1,-0.4,0,0.6,1}) for(double y : new double[]{-1,0,1}){
                Vec3 direction = ScmActuatorGeometry.gimbalDirection(facing,x,y,30);
                Vec3 coordinates = ScmActuatorGeometry.gimbalCoordinates(facing,direction.scale(49),30);
                assertEquals(x,coordinates.x,1e-9);
                assertEquals(y,coordinates.y,1e-9);
            }
        }
    }

    @Test void verticalNativeGimbalsReverseTheirTangentBasis(){
        Vec3 up = ScmActuatorGeometry.gimbalDirection(new Vec3(0,1,0),1,1,30);
        assertTrue(up.x > 0 && up.z > 0);
        Vec3 down = ScmActuatorGeometry.gimbalDirection(new Vec3(0,-1,0),1,1,30);
        assertTrue(down.x < 0 && down.z > 0);
    }

    @Test void impossibleWrenchesStayInsideThrustConeAndCapacity(){
        var units = List.of(unit(new Vec3(4,0,0)),unit(new Vec3(-4,0,0)));
        var res = ScmVectorThrustAllocator.allocate(units,new Vec3(1000,-1000,1000),new Vec3(1000,1000,1000),true);
        for(Vec3 force : res.forces()){
            assertTrue(force.length() <= 100.000001);
            assertTrue(force.y >= -1e-9);
            assertTrue(Math.hypot(force.x,force.z) <= force.y * Math.tan(Math.PI/6) + 1e-8);
        }
    }

    private static ScmPrecisionAllocator.Unit unit(Vec3 arm){
        Vec3 force = new Vec3(0,100,0);
        return new ScmPrecisionAllocator.Unit(force,arm.cross(force),false,arm,30);
    }
}
