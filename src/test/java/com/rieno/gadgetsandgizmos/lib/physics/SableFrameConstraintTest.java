package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
<<<<<<< Updated upstream
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
=======
>>>>>>> Stashed changes
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

<<<<<<< Updated upstream
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
=======
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
>>>>>>> Stashed changes
    }
}
