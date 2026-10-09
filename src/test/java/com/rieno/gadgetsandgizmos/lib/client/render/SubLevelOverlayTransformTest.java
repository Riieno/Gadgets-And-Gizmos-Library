package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Overlay anchors must survive plot coordinates, interpolation and every ship angle
class SubLevelOverlayTransformTest{
    @Test
    void cameraRelativeAnchorRetainsPrecisionAtLargePlotCoordinates(){
        Vec3 origin = new Vec3(20481032.5, 130.5, 20483080.5);
        Vec3 camera = new Vec3(12.25, 22.5, 34.75);
        for(double pitch : new double[]{0, .4, Math.PI / 2, Math.PI}){
            for(double yaw : new double[]{0, .8, Math.PI / 2, Math.PI}){
                var rotation = new Quaterniond().rotateY(yaw).rotateX(pitch).rotateZ(.3);
                var pose = new Pose3d(new Vector3d(12, 24, 36), rotation,
                        new Vector3d(origin.x, origin.y, origin.z), new Vector3d(1));
                var matrix = SubLevelClientRenderApi.localModelView(pose, origin, camera, new Matrix4f());
                var expected = pose.transformPosition(origin).subtract(camera);
                var actual = matrix.transformPosition(new Vector3f());
                assertEquals(expected.x, actual.x, 1.0E-6);
                assertEquals(expected.y, actual.y, 1.0E-6);
                assertEquals(expected.z, actual.z, 1.0E-6);
                var local = new Vector3f(.25F, 1.5F, -.75F);
                var point = pose.transformPosition(origin.add(local.x, local.y, local.z)).subtract(camera);
                matrix.transformPosition(local);
                assertEquals(point.x, local.x, 1.0E-6);
                assertEquals(point.y, local.y, 1.0E-6);
                assertEquals(point.z, local.z, 1.0E-6);
            }
        }
    }

    @Test
    void overlayUsesRenderInterpolationAndSupportsOrdinaryWorldBlocks(){
        var body = mock(ClientSubLevel.class);
        var pose = new Pose3d(new Vector3d(2, 4, 6), new Quaterniond(), new Vector3d(), new Vector3d(2));
        when(body.renderPose(.35F)).thenReturn(pose);
        var matrix = SubLevelClientRenderApi.localModelView(body, .35F, Vec3.ZERO, Vec3.ZERO, new Matrix4f());
        verify(body).renderPose(.35F);
        verify(body, never()).logicalPose();
        assertEquals(2F, matrix.m00());
        var world = SubLevelClientRenderApi.localModelView((ClientSubLevel) null, .35F,
                new Vec3(10, 20, 30), new Vec3(9, 18, 27), new Matrix4f());
        assertEquals(new Vector3f(1, 2, 3), world.transformPosition(new Vector3f()));
    }

    // Detached component slots must not move the projection or change the configured up and forward
    @Test
    void detachedProjectionUsesRealAnchorAndCraftAxes(){
        var body = mock(ClientSubLevel.class);
        Vec3 anchor = new Vec3(20481032.5, 130.5, 20483080.5);
        Vec3 origin = anchor.add(32, 8, -16);
        Vec3 camera = new Vec3(10, 20, 30);
        for(double pitch : new double[]{0, Math.PI / 2, Math.PI}){
            var rotation = new Quaterniond().rotateY(.8).rotateX(pitch);
            var pose = new Pose3d(new Vector3d(12, 24, 36), rotation,
                    new Vector3d(anchor.x, anchor.y, anchor.z), new Vector3d(1));
            when(body.renderPose(.35F)).thenReturn(pose);
            var frame = new com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation(
                    net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.NORTH);
            var hosted = SubLevelClientRenderApi.anchoredPose(body, .35F, origin, anchor, frame.rotation());
            var matrix = SubLevelClientRenderApi.localModelView(hosted, origin, camera, new Matrix4f());
            var center = matrix.transformPosition(new Vector3f());
            var world = pose.transformPosition(anchor).subtract(camera);
            assertEquals(world.x, center.x, 1.0E-6);
            assertEquals(world.y, center.y, 1.0E-6);
            assertEquals(world.z, center.z, 1.0E-6);
            var expected = rotation.transform(new Vector3d(frame.upVector().x, frame.upVector().y, frame.upVector().z));
            var up = matrix.transformDirection(new Vector3f(0, 1, 0));
            assertEquals(expected.x, up.x, 1.0E-6);
            assertEquals(expected.y, up.y, 1.0E-6);
            assertEquals(expected.z, up.z, 1.0E-6);
        }
    }
}
