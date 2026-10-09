package com.rieno.gadgetsandgizmos.lib.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Aim a two-joint rig in a mounting frame or a stable world frame
public final class ViewRig{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Select fixed upward, automatic patrol or explicit scalar aiming
    public enum Mode{ LOCKED, SENTRY, MANUAL }
    // Select the mounted or world orientation frame
    public enum Orientation{ LOCAL, GLOBAL }
    // Retain the explicit aim while a remote viewer controls the rig
    private double pan;
    private double tilt;
    private double fov = 70.0D;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Clamp explicit pan and tilt without wrapping their endpoint values
    public void aim(double pan, double tilt){
        this.pan = clamp(pan, -180, 180);
        this.tilt = clamp(tilt, -135, 135);
    }

    // Apply bounded mouse motion to the retained aim
    public void turn(double panDelta, double tiltDelta){
        aim(pan + clamp(panDelta, -45, 45), tilt + clamp(tiltDelta, -45, 45));
    }

    // Apply scroll zoom to the retained field of view
    public void zoom(double steps){
        fov = clamp(fov - clamp(steps, -10, 10) * 5.0D, 5, 120);
    }

    // Set a persisted field of view
    public void setFov(double fov){
        this.fov = clamp(fov, 5, 120);
    }

    // Read the explicit pan angle
    public double pan(){ return pan; }
    // Read the explicit tilt angle
    public double tilt(){ return tilt; }
    // Read the current field of view
    public double fov(){ return fov; }

    // Resolve one patrol sample without accumulating tick drift
    public static Angles angles(Mode mode, double time, double pan, double tilt){
        return switch(mode){
            case LOCKED -> new Angles(0, 0);
            case MANUAL -> new Angles(clamp(pan, -180, 180), clamp(tilt, -135, 135));
            case SENTRY -> new Angles(180.0D * Math.sin(time * Math.PI / 240.0D),
                    -90.0D + 45.0D * Math.sin(time * Math.PI / 173.0D));
        };
    }

    // Measure tilt from the mounting Up face after applying stand pan
    public static Quaterniond orientation(Angles angles){
        Joints joints = joints(angles);
        return new Quaterniond(joints.stand()).mul(joints.camera());
    }

    // Keep pitch on the stand's horizontal hinge with zero tilt looking Up
    public static Joints joints(Angles angles){
        // Avoid the incorrect Quaterniond.rotationX helper in JOML 1.10.5
        return new Joints(new Quaterniond().rotationY(Math.toRadians(angles.pan())),
                new Quaterniond().rotationAxis(Math.toRadians(90.0D + angles.tilt()), 1, 0, 0));
    }

    // Resolve a world aim while retaining the mounted basis in Local mode
    public static Quaterniond worldOrientation(Quaterniondc mount, Orientation mode, Angles angles){
        Quaterniond aim = orientation(angles);
        return mode == Orientation.GLOBAL ? aim : new Quaterniond(mount).mul(aim);
    }

    // Split a stabilised orientation into the stand and its camera child
    public static Joints joints(Quaterniondc mount, Quaterniondc world){
        Quaterniond local = new Quaterniond(mount).invert().mul(world);
        Vector3d dir = local.transform(new Vector3d(0, 0, -1));
        double pan = Math.atan2(-dir.x, -dir.z);
        Quaterniond stand = new Quaterniond().rotationY(pan);
        Quaterniond camera = new Quaterniond(stand).invert().mul(local);
        return new Joints(stand, camera);
    }

    // Reject non-finite control values before they reach transforms
    private static double clamp(double val, double min, double max){
        return Double.isFinite(val) ? Math.clamp(val, min, max) : Math.clamp(0.0D, min, max);
    }

    // Carry one finite pan and tilt sample
    public record Angles(double pan, double tilt){}
    // Carry separate stand and camera transformations
    public record Joints(Quaterniondc stand, Quaterniondc camera){
        // Retain separate immutable joint snapshots
        public Joints{
            stand = new Quaterniond(stand);
            camera = new Quaterniond(camera);
        }
        // Return a separate stand rotation
        @Override
        public Quaterniondc stand(){ return new Quaterniond(stand); }
        // Return a separate camera rotation
        @Override
        public Quaterniondc camera(){ return new Quaterniond(camera); }
    }
}
