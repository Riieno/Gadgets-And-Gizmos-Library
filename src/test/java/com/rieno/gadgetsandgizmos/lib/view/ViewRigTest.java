package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify lens geometry and stabilization independently of rendering
class ViewRigTest{
    // Verify the camera tilts in the already-panned stand frame
    @Test
    void panCarriesTheTiltedCamera(){
        ViewPose pose = new ViewPose(Vec3.ZERO, ViewRig.orientation(new ViewRig.Angles(90, -45)), 70);
        assertEquals(-Math.sqrt(0.5D), pose.forward().x, 1.0E-9D);
        assertEquals(Math.sqrt(0.5D), pose.forward().y, 1.0E-9D);
        assertEquals(0, pose.forward().z, 1.0E-9D);
    }
    // Verify global aiming cancels arbitrary body yaw, pitch and roll in both joints
    @Test
    void globalJointsReconstructAStableWorldPose(){
        Quaterniond mount = new Quaterniond().rotationYXZ(1.2D, -0.7D, 0.4D);
        var angles = new ViewRig.Angles(47, -38);
        Quaterniond world = ViewRig.worldOrientation(mount, ViewRig.Orientation.GLOBAL, angles);
        var joints = ViewRig.joints(mount, world);
        Quaterniond rebuilt = new Quaterniond(mount).mul(joints.stand()).mul(joints.camera());
        for(Vector3d axis : new Vector3d[]{new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, -1)}){
            assertTrue(world.transform(new Vector3d(axis)).distance(rebuilt.transform(new Vector3d(axis))) < 1.0E-9D);
        }
        Quaterniond local = ViewRig.worldOrientation(mount, ViewRig.Orientation.LOCAL, angles);
        assertTrue(world.transform(new Vector3d(0, 1, 0)).distance(local.transform(new Vector3d(0, 1, 0))) > 0.1D);
    }
    // Verify fixed upward and bounded bidirectional sentry samples
    @Test
    void modesKeepTheirFullPanAndTiltBounds(){
        var locked = ViewRig.angles(ViewRig.Mode.LOCKED, 100, 45, -90);
        assertEquals(1, new ViewPose(Vec3.ZERO, ViewRig.orientation(locked), 70).forward().y, 1.0E-9D);
        assertEquals(180, ViewRig.angles(ViewRig.Mode.MANUAL, 0, 900, -900).pan());
        assertEquals(-135, ViewRig.angles(ViewRig.Mode.MANUAL, 0, 900, -900).tilt());
        assertEquals(180, ViewRig.angles(ViewRig.Mode.SENTRY, 120, 0, 0).pan(), 1.0E-9D);
        assertEquals(-180, ViewRig.angles(ViewRig.Mode.SENTRY, 360, 0, 0).pan(), 1.0E-9D);
        assertEquals(-45, ViewRig.angles(ViewRig.Mode.SENTRY, 86.5D, 0, 0).tilt(), 1.0E-9D);
    }
    // Verify the mounting Up face is the zero reference for the full tilt sweep
    @Test
    void zeroTiltLooksUpAndBothLimitsExtendPastTheHorizon(){
        for(double pan : new double[]{-180, -90, 0, 90, 180}){
            ViewPose pose = new ViewPose(Vec3.ZERO, ViewRig.orientation(new ViewRig.Angles(pan, 0)), 70);
            assertEquals(0, pose.forward().x, 1.0E-9D);
            assertEquals(1, pose.forward().y, 1.0E-9D);
            assertEquals(0, pose.forward().z, 1.0E-9D);
        }
        assertEquals(-1, new ViewPose(Vec3.ZERO,
                ViewRig.orientation(new ViewRig.Angles(0, -90)), 70).forward().z, 1.0E-9D);
        assertEquals(1, new ViewPose(Vec3.ZERO,
                ViewRig.orientation(new ViewRig.Angles(0, 90)), 70).forward().z, 1.0E-9D);
        for(double tilt : new double[]{-135, 135}){
            assertEquals(-Math.sqrt(0.5D), new ViewPose(Vec3.ZERO,
                    ViewRig.orientation(new ViewRig.Angles(0, tilt)), 70).forward().y, 1.0E-9D);
        }
    }
    // Verify pitch cannot turn the stand or move the lens sideways from its hinge
    @Test
    void pitchUsesTheCameraHingeIndependentlyOfStandPan(){
        var neutral = ViewRig.joints(new ViewRig.Angles(73, 0));
        for(double tilt : new double[]{-135, -90, -45, 0, 45, 90, 135}){
            var joints = ViewRig.joints(new ViewRig.Angles(73, tilt));
            assertEquals(neutral.stand(), joints.stand());
            Vector3d hinge = joints.camera().transform(new Vector3d(1, 0, 0));
            assertEquals(1, hinge.x, 1.0E-9D);
            assertEquals(0, hinge.y, 1.0E-9D);
            assertEquals(0, hinge.z, 1.0E-9D);
            Quaterniond rebuilt = new Quaterniond(joints.stand()).mul(joints.camera());
            assertTrue(rebuilt.transform(new Vector3d(0, 0, -1)).distance(
                    ViewRig.orientation(new ViewRig.Angles(73, tilt)).transform(new Vector3d(0, 0, -1))) < 1.0E-9D);
        }
    }
    // Verify control input cannot create invalid transforms or a zero FOV
    @Test
    void invalidMouseValuesRemainFinite(){
        ViewRig rig = new ViewRig();
        rig.aim(Double.NaN, Double.POSITIVE_INFINITY);
        rig.turn(Double.POSITIVE_INFINITY, Double.NaN);
        rig.zoom(Double.NaN);
        assertEquals(0, rig.pan());
        assertEquals(0, rig.tilt());
        for(int idx = 0; idx < 100; idx++) rig.zoom(10);
        assertEquals(5, rig.fov());
        for(int idx = 0; idx < 100; idx++) rig.zoom(-10);
        assertEquals(120, rig.fov());
    }
    // Verify callers cannot mutate the retained pose or joint quaternions
    @Test
    void poseAndJointValuesOwnTheirRotations(){
        Quaterniond rotation = new Quaterniond();
        ViewPose pose = new ViewPose(Vec3.ZERO, rotation, 70);
        rotation.rotateX(1);
        ((Quaterniond) pose.orientation()).rotateY(2);
        assertEquals(new Vec3(0, 0, -1), pose.forward());
        var joints = new ViewRig.Joints(rotation, rotation);
        var original = new Quaterniond(joints.stand());
        ((Quaterniond) joints.stand()).identity();
        rotation.identity();
        assertEquals(original, joints.stand());
    }
}
