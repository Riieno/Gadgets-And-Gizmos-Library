package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep mounted joints inside Sable's solver and preserve their local attachment frames
class SableConstraintApiTest{
    // Initialize registries before mocking Sable bodies
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // Keep plot coordinates local and copy the frame before the caller changes its target
    @Test void fixedFramesLockAllAxesWithoutMovingTheBodies() throws Exception{
        var pipeline = mock(PhysicsPipeline.class);
        var parent = mock(PhysicsPipelineBody.class);
        var child = mock(PhysicsPipelineBody.class);
        var handle = mock(GenericConstraintHandle.class);
        when(pipeline.addConstraint(eq(parent), eq(child), any(GenericConstraintConfiguration.class))).thenReturn(handle);
        var anchor = new Vector3d(30000004.5, 70.5, 30000008.5);
        var mounted = new Vector3d(31000004.5, 70.5, 31000008.5);
        var orientation = new Quaterniond().rotateX(0.3).rotateZ(-0.4).rotateY(1.2);
        var expected = new Quaterniond(orientation).normalize();
        assertSame(handle, SableConstraintApi.fixedFrameConstraint(pipeline, parent, child, anchor, mounted, orientation));
        anchor.zero();
        orientation.identity();
        var config = ArgumentCaptor.forClass(GenericConstraintConfiguration.class);
        verify(pipeline).addConstraint(eq(parent), eq(child), config.capture());
        assertEquals(30000004.5, config.getValue().pos1().x());
        assertEquals(mounted, config.getValue().pos2());
        assertEquals(expected, config.getValue().orientation1());
        assertEquals(new Quaterniond(), config.getValue().orientation2());
        assertEquals(Set.of(ConstraintJointAxis.ALL), config.getValue().lockedAxes());
        SableConstraintApi.setFrame(handle, 1, anchor, orientation);
        verify(handle).setFrame1(anchor, orientation);
        verify(handle).setContactsEnabled(false);
        verifyNoMoreInteractions(pipeline);
        verifyNoInteractions(parent, child);
    }

    // Permit a stationary world anchor on either side without rewriting its coordinates
    @Test void rotaryJointsPreserveWorldAnchorsAndSeparateLocalAxes(){
        var pipeline = mock(PhysicsPipeline.class);
        var body = mock(PhysicsPipelineBody.class);
        var handle = mock(RotaryConstraintHandle.class);
        when(pipeline.addConstraint(isNull(), eq(body), any(RotaryConstraintConfiguration.class))).thenReturn(handle);
        when(pipeline.addConstraint(eq(body), isNull(), any(RotaryConstraintConfiguration.class))).thenReturn(handle);
        var anchor = new Vector3d(15.5, 80.5, -23.5);
        var mounted = new Vector3d(30000000.5, 80.5, 30000000.5);
        assertSame(handle, SableConstraintApi.rotaryConstraint(pipeline, null, body, anchor, mounted,
                new Vector3d(0, -2, 0), new Vector3d(4, 0, 0)));
        var config = ArgumentCaptor.forClass(RotaryConstraintConfiguration.class);
        verify(pipeline).addConstraint(isNull(), eq(body), config.capture());
        assertEquals(anchor, config.getValue().pos1());
        assertEquals(mounted, config.getValue().pos2());
        assertEquals(new Vector3d(0, -1, 0), config.getValue().normal1());
        assertEquals(new Vector3d(1, 0, 0), config.getValue().normal2());
        assertSame(handle, SableConstraintApi.rotaryConstraint(pipeline, body, null, mounted, anchor,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0)));
        verify(pipeline).addConstraint(eq(body), isNull(), any(RotaryConstraintConfiguration.class));
        verifyNoMoreInteractions(pipeline);
        verifyNoInteractions(body);
    }

    // Reject self-joints before the backend sees them
    @Test void coincidentBodiesDoNotCreateConstraints(){
        var pipeline = mock(PhysicsPipeline.class);
        var body = mock(PhysicsPipelineBody.class);
        var pos = new Vector3d();
        var axis = new Vector3d(0, 1, 0);
        assertNull(SableConstraintApi.rotaryConstraint(pipeline, body, body, pos, pos, axis, axis));
        assertNull(SableConstraintApi.rotaryConstraint(pipeline, null, null, pos, pos, axis, axis));
        assertNull(SableConstraintApi.fixedFrameConstraint(pipeline, body, body, pos, pos, new Quaterniond()));
        assertNull(SableConstraintApi.fixedFrameConstraint(pipeline, null, null, pos, pos, new Quaterniond()));
        verifyNoInteractions(pipeline);
    }

    // Measure each body's inertia along its own hinge axis
    @Test void servoUsesBothLocalInertiasAndKeepsTheRequestedAngle(){
        var parent = body(new Matrix3d().scaling(20, 30, 40));
        var child = body(new Matrix3d().scaling(70, 80, 90));
        var handle = mock(RotaryConstraintHandle.class);
        SableConstraintApi.setRotaryServo(handle, parent, child, new Vector3d(1, 0, 0), new Vector3d(0, 0, 1),
                -0.75, 2, 3);
        verify(handle).setMotor(RotaryConstraintHandle.DEFAULT_AXIS, -0.75, 180, 270, false, 0);
        verify(handle).setContactsEnabled(false);
        verify(parent).getMassTracker();
        verify(child).getMassTracker();
        verifyNoMoreInteractions(parent, child);
    }

    // Keep world-mounted heads responsive and use the same coefficients when body order is reversed
    @Test void worldMountedServoUsesTheFiniteBodyInertia(){
        var body = body(new Matrix3d().scaling(20, 30, 40));
        var handle = mock(RotaryConstraintHandle.class);
        var axis = new Vector3d(0, -1, 0);
        SableConstraintApi.setRotaryServo(handle, null, body, axis, axis, 0.5, 2, 3);
        SableConstraintApi.setRotaryServo(handle, body, null, axis, axis, 0.5, 2, 3);
        verify(handle, times(2)).setMotor(RotaryConstraintHandle.DEFAULT_AXIS, 0.5, 60, 90, false, 0);
    }

    // Retain Simulated's minimum inertia for light bearing plates
    @Test void lightHeadsKeepTheMinimumServoResponse(){
        var body = body(new Matrix3d().scaling(0.1, 0.2, 0.3));
        var handle = mock(RotaryConstraintHandle.class);
        var axis = new Vector3d(1, 0, 0);
        SableConstraintApi.setRotaryServo(handle, null, body, axis, axis, 0.25, 2, 3);
        verify(handle).setMotor(RotaryConstraintHandle.DEFAULT_AXIS, 0.25, 20, 30, false, 0);
    }

    // Supply anisotropic mass data without creating a native physics scene
    private static ServerSubLevel body(Matrix3d inertia){
        var body = mock(ServerSubLevel.class);
        var mass = mock(MassData.class);
        when(body.getMassTracker()).thenReturn(mass);
        when(mass.getInertiaTensor()).thenReturn(inertia);
        clearInvocations(body);
        return body;
    }
}
