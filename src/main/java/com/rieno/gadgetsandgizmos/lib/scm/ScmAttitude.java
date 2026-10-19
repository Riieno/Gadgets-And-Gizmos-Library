package com.rieno.gadgetsandgizmos.lib.scm;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Measure the configured nose and up axes, with positive roll lowering the right side
public record ScmAttitude(double pitch, double yaw, double roll){
    public static ScmAttitude measure(Quaterniondc bodyRotation, ScmOrientation frame){
        var rotation = new Quaterniond(bodyRotation).mul(frame.rotation()).normalize();
        var forward = rotation.transform(new Vector3d(0, 0, -1));
        var up = rotation.transform(new Vector3d(0, 1, 0));
        var reference = new Vector3d(0, 1, 0).sub(new Vector3d(forward).mul(forward.y));
        double pitch = Math.toDegrees(Math.asin(Math.clamp(forward.y, -1, 1)));
        double yaw = Math.toDegrees(Math.atan2(forward.x, -forward.z));
        double roll = 0;
        if(reference.lengthSquared() > 1.0E-12D){
            reference.normalize();
            var actual = new Vector3d(up).sub(new Vector3d(forward).mul(up.dot(forward))).normalize();
            roll = Math.toDegrees(Math.atan2(forward.dot(reference.cross(actual, new Vector3d())),
                    Math.clamp(reference.dot(actual), -1, 1)));
        }
        return new ScmAttitude(pitch, yaw, roll);
    }
}
