package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.*;

class ViewTargetFramingTest{
    @Test void centresTargetsInEveryMountAndWorldFrame(){
        for(Quaterniond mount : new Quaterniond[]{new Quaterniond(), new Quaterniond().rotationY(1.1),
                new Quaterniond().rotationAxis(1.8, 1, 0, 0), new Quaterniond().rotationXYZ(.8, -.4, 1.3)}){
            for(ViewRig.Orientation frame : ViewRig.Orientation.values()){
                for(Vec3 target : new Vec3[]{new Vec3(0, 0, -10), new Vec3(5, -20, 8),
                        new Vec3(-30, 12, 1), new Vec3(0, -10, 0), new Vec3(0, 10, 0)}){
                    ViewRig rig = new ViewRig();
                    rig.track(ViewTargetFraming.aim(Vec3.ZERO, target, mount, frame));
                    Vec3 forward = new ViewPose(Vec3.ZERO, ViewRig.worldOrientation(mount, frame,
                            new ViewRig.Angles(rig.pan(), rig.tilt())), 70).forward();
                    assertEquals(1, forward.dot(target.normalize()), 1.0E-9);
                }
            }
        }
    }

    @Test void fitsEveryProjectedCornerIncludingPortraitFrames(){
        AABB bounds = new AABB(-3, -1, -25, 3, 4, -15);
        for(double aspect : new double[]{.6, 1, 16.0 / 9}){
            var angles = ViewTargetFraming.aim(Vec3.ZERO, bounds.getCenter(), new Quaterniond(), ViewRig.Orientation.GLOBAL);
            ViewPose pose = new ViewPose(Vec3.ZERO, ViewRig.orientation(angles), 70);
            double tangent = Math.tan(Math.toRadians(ViewTargetFraming.fieldOfView(pose, bounds, aspect) / 2));
            for(int idx = 0; idx < 8; idx++){
                Vector3d corner = pose.orientation().transformInverse(new Vector3d(
                        (idx & 1) == 0 ? bounds.minX : bounds.maxX,
                        (idx & 2) == 0 ? bounds.minY : bounds.maxY,
                        (idx & 4) == 0 ? bounds.minZ : bounds.maxZ));
                assertTrue(Math.abs(corner.y / corner.z) < tangent);
                assertTrue(Math.abs(corner.x / corner.z) < tangent * aspect);
            }
        }
    }

    @Test void widensAsTargetsApproachAndClampsImpossibleFraming(){
        ViewPose pose = new ViewPose(Vec3.ZERO, new Quaterniond(), 70);
        double far = ViewTargetFraming.fieldOfView(pose, new AABB(-1, -2, -51, 1, 2, -50), 1);
        double near = ViewTargetFraming.fieldOfView(pose, new AABB(-1, -2, -6, 1, 2, -5), 1);
        assertTrue(near > far);
        assertEquals(5, ViewTargetFraming.fieldOfView(pose, new AABB(0, 0, -100, 0, 0, -100), 1));
        assertEquals(120, ViewTargetFraming.fieldOfView(pose, new AABB(-1, -1, -1, 1, 1, 1), 1));
    }

    @Test void automaticZoomWidensImmediatelyAndFinishesItsInwardTransition(){
        ViewRig rig = new ViewRig();
        rig.setFov(20);
        rig.frameTarget(80);
        assertEquals(80, rig.fov());
        rig.frameTarget(10);
        assertTrue(rig.fov() > 10 && rig.fov() < 80);
        for(int idx = 0; idx < 40; idx++) rig.frameTarget(10);
        assertEquals(10, rig.fov());
    }

    @Test void portraitHostsWidenTheSamePoseAndBoundsSurviveSynchronization(){
        AABB bounds = new AABB(-6, -1, -20, 6, 1, -15);
        ViewPose pose = new ViewPose(Vec3.ZERO, new Quaterniond(), 30);
        assertTrue(ViewTargetFraming.fit(pose, bounds, .5).fov() > ViewTargetFraming.fit(pose, bounds, 2).fov());
        assertEquals(pose.position(), ViewTargetFraming.fit(pose, bounds, .5).position());
        assertEquals(bounds, ViewTargetFraming.boundsFromTag(ViewTargetFraming.boundsTag(bounds)));
        assertNull(ViewTargetFraming.boundsFromTag(new CompoundTag()));
        CompoundTag bad = ViewTargetFraming.boundsTag(bounds);
        bad.putDouble("MinX", Double.NaN);
        assertNull(ViewTargetFraming.boundsFromTag(bad));
    }
}
