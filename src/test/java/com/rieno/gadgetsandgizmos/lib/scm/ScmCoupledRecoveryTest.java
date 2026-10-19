package com.rieno.gadgetsandgizmos.lib.scm;

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Tensor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScmCoupledRecoveryTest{
    private static final Tensor INERTIA=new Tensor(40,2,3,2,60,4,3,4,80);
    private static final Vec3 UP=new Vec3(0,1,0);

    @Test void transverseSwayGetsOpposingForcesWithoutAFormationLock(){
        Vec3 rate=new Vec3(4,0,0);
        var first=carriage(true,new Vec3(0,0,0),Vec3.ZERO,Vec3.ZERO);
        var second=carriage(false,new Vec3(0,0,8),rate,Vec3.ZERO);
        var requested=ScmArticulatedFlightControl.requests(List.of(first,second),Vec3.ZERO,Vec3.ZERO,UP.scale(200),0.05,0.1,0.02);
        assertTrue(requested.get(0).force().x>0);
        assertTrue(requested.get(1).force().x<0);
        assertEquals(0,requested.get(0).force().add(requested.get(1).force()).length(),1e-8);
        assertTrue(requested.get(1).force().dot(rate)<0);
    }

    @Test void allJointRotationAxesDissipateEnergyUsingTheFullInertia(){
        for(Vec3 axis:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),new Vec3(1,2,-3).normalize())){
            var first=carriage(true,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO);
            var second=carriage(false,new Vec3(0,0,8),Vec3.ZERO,axis);
            var requested=ScmArticulatedFlightControl.requests(List.of(first,second),Vec3.ZERO,Vec3.ZERO,UP.scale(200),0.05,0.1,0.02);
            assertTrue(requested.get(1).torque().dot(axis)<0,"Joint spin was not damped: "+axis);
            assertTrue(requested.get(0).torque().dot(axis)>0,"Lead received no opposing joint reaction: "+axis);
        }
    }

    @Test void commandedCollectiveYawDoesNotReceiveTransverseOrJointBraking(){
        Vec3 spin=new Vec3(0,0.5,0), separation=new Vec3(0,0,8), requestedYaw=new Vec3(0,100,0);
        var first=carriage(true,Vec3.ZERO,Vec3.ZERO,spin);
        var second=carriage(false,separation,spin.cross(separation),spin);
        var requested=ScmArticulatedFlightControl.requests(List.of(first,second),Vec3.ZERO,requestedYaw,UP.scale(200),0.05,0.1,0.02);
        assertEquals(0,requested.get(0).force().length(),1e-8);
        assertEquals(0,requested.get(1).force().length(),1e-8);
        assertEquals(0,requested.get(0).torque().distanceTo(requestedYaw),1e-8);
        assertEquals(0,requested.get(1).torque().length(),1e-8);
    }

    @Test void coupledYawRecoversAcrossRepeatedControlTicks(){
        Vec3 firstRate=Vec3.ZERO, secondRate=new Vec3(0,2,0);
        for(int tick=0;tick<120;tick++){
            var first=carriage(true,Vec3.ZERO,Vec3.ZERO,firstRate);
            var second=carriage(false,new Vec3(0,0,8),firstRate.cross(new Vec3(0,0,8)),secondRate);
            var requested=ScmArticulatedFlightControl.requests(List.of(first,second),Vec3.ZERO,Vec3.ZERO,UP.scale(200),0.05,0.1,0.02);
            Vec3 before=secondRate.subtract(firstRate);
            firstRate=firstRate.add(INERTIA.inverse().transform(requested.get(0).torque()).scale(0.05));
            secondRate=secondRate.add(INERTIA.inverse().transform(requested.get(1).torque()).scale(0.05));
            assertTrue(secondRate.subtract(firstRate).length()<=before.length()+1e-8);
        }
        assertTrue(secondRate.subtract(firstRate).length()<0.005);
    }

    @Test void authorityBoundsPreserveTheExistingWrenchResidualTolerance(){
        var model=ScmAdaptiveStateModel.sample(List.of(new ScmAdaptiveStateModel.Actuator(
                new Vec3(0,10,0),Vec3.ZERO,0)),0.05,0.1,0.02);
        assertTrue(model.authority(new Vec3(0.05,1,0),false,true)>9);
        assertEquals(0,model.authority(new Vec3(0.2,1,0),false,true),1e-9);
    }

    private static ScmArticulatedFlightControl.Carriage carriage(boolean primary,Vec3 pos,Vec3 velocity,Vec3 rotation){
        List<ScmPrecisionAllocator.Unit> units=new ArrayList<>();
        for(Vec3 axis:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
            units.add(new ScmPrecisionAllocator.Unit(axis.scale(10000),Vec3.ZERO,true));
            units.add(new ScmPrecisionAllocator.Unit(Vec3.ZERO,axis.scale(10000),true));
        }
        return new ScmArticulatedFlightControl.Carriage(UUID.randomUUID(),primary,10,INERTIA,pos,velocity,rotation,UP,units);
    }
}
