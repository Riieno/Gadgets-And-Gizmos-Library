package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep many moving or stationary attachments inside the solver without joint churn
class SableFrameConstraintTest{
    @Test void movingAnchorAndOrientationReuseOneJointAndStationaryTargetsStayAsleep(){
        var pipeline = mock(PhysicsPipeline.class);
        var body = mock(PhysicsPipelineBody.class);
        var handle = mock(GenericConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(body), any(GenericConstraintConfiguration.class))).thenReturn(handle);
        var local = new Vector3d(30000004.5, 70.5, 30000008.5);
        var anchor = new Vector3d(10, 80, 20);
        var rotation = new Quaterniond();
        var attachment = new SableFrameConstraint();
        assertTrue(attachment.update(pipeline, null, body, anchor, local, rotation));
        clearInvocations(pipeline, handle);
        for(int idx = 0; idx < 100; idx++) assertTrue(attachment.update(pipeline, null, body, anchor, local, rotation));
        verify(pipeline, never()).wakeUp(any());
        verify(handle, never()).setFrame1(any(), any());
        anchor.add(0.5, 0, 0);
        rotation.rotateY(0.4);
        assertTrue(attachment.update(pipeline, null, body, anchor, local, rotation));
        verify(handle).setFrame1(eq(anchor), eq(rotation));
        verify(handle, never()).remove();
        verify(pipeline, never()).addConstraint(any(), any(), any(GenericConstraintConfiguration.class));
        verify(pipeline).wakeUp(body);
        verify(pipeline, never()).teleport(any(), any(), any());
        verify(pipeline, never()).resetVelocity(any());
        attachment.close();
        attachment.close();
        verify(handle).remove();
    }

    @Test void removedBodiesAreUnavailableBeforeMutatingTheirNativeJoint(){
        var pipeline = mock(PhysicsPipeline.class);
        var body = mock(PhysicsPipelineBody.class);
        var handle = mock(GenericConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(body), any(GenericConstraintConfiguration.class))).thenReturn(handle);
        var attachment = new SableFrameConstraint();
        var pos = new Vector3d();
        assertTrue(attachment.update(pipeline, null, body, pos, pos, new Quaterniond()));
        clearInvocations(pipeline, handle);
        when(body.isRemoved()).thenReturn(true);
        assertFalse(attachment.isValid());
        assertFalse(attachment.update(pipeline, null, body, new Vector3d(1, 0, 0), pos, new Quaterniond()));
        verify(handle).remove();
        verify(handle, never()).setFrame1(any(), any());
        verify(pipeline, never()).addConstraint(any(), any(), any(GenericConstraintConfiguration.class));
        verify(pipeline, never()).wakeUp(any());
    }

    @Test void changedBodiesAndInvalidFramesReleaseTheOldAttachment(){
        var pipeline = mock(PhysicsPipeline.class);
        var first = mock(PhysicsPipelineBody.class);
        var next = mock(PhysicsPipelineBody.class);
        var firstHandle = mock(GenericConstraintHandle.class);
        var nextHandle = mock(GenericConstraintHandle.class);
        when(firstHandle.isValid()).thenReturn(true);
        when(nextHandle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(first), any(GenericConstraintConfiguration.class))).thenReturn(firstHandle);
        when(pipeline.addConstraint(isNull(), eq(next), any(GenericConstraintConfiguration.class))).thenReturn(nextHandle);
        var attachment = new SableFrameConstraint();
        var pos = new Vector3d();
        assertTrue(attachment.update(pipeline, null, first, pos, pos, new Quaterniond()));
        assertTrue(attachment.update(pipeline, null, next, pos, pos, new Quaterniond()));
        verify(firstHandle).remove();
        assertFalse(attachment.update(pipeline, null, next, pos, pos, new Quaterniond(0, 0, 0, 0)));
        verify(nextHandle).remove();
        assertFalse(attachment.isValid());
    }
}
