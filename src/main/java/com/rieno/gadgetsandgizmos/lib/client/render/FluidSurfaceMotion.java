package com.rieno.gadgetsandgizmos.lib.client.render;

import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

// Let a fluid surface lag behind changes in the container's orientation and settle afterwards
public final class FluidSurfaceMotion{
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f velocity = new Vector3f();
    private double prevTime = Double.NaN;

    // Convert an upward direction from view coordinates into the item's local coordinates
    public static Vector3f localUp(Matrix4fc modelView, Vector3fc viewUp){
        var inverse = new Matrix3f(modelView);
        if(!Float.isFinite(inverse.determinant()) || Math.abs(inverse.determinant()) < 1.0E-9F) return new Vector3f(0, 1, 0);
        var res = inverse.invert().transform(new Vector3f(viewUp));
        return res.isFinite() && res.lengthSquared() > 1.0E-8F ? res.normalize() : new Vector3f(0, 1, 0);
    }

    // Accept local upward gravity and render time in ticks; repeated presentations do not advance the simulation
    public Vector3f update(Vector3fc target, double time){
        var direction = new Vector3f(target);
        if(!direction.isFinite() || direction.lengthSquared() < 1.0E-8F) direction.set(0, 1, 0);
        direction.normalize();
        if(!Double.isFinite(time)) time = 0;
        double elapsed = time - prevTime;
        if(!Double.isFinite(prevTime) || elapsed < 0 || elapsed > 10){
            up.set(direction);
            velocity.zero();
        }else if(elapsed > 0){
            float remaining = (float)(elapsed / 20);
            var force = new Vector3f();
            while(remaining > 0){
                float step = Math.min(remaining, 1F / 120);
                float dot = Math.clamp(up.dot(direction), -1, 1);
                force.set(direction).fma(-dot, up);
                if(force.lengthSquared() < 1.0E-10F && dot < 0){
                    force.set(Math.abs(up.y) < .9F ? 0 : 1, Math.abs(up.y) < .9F ? 1 : 0, 0).cross(up);
                }
                if(force.lengthSquared() > 1.0E-10F) force.normalize().mul((float)Math.acos(dot) * 90 * step);
                velocity.add(force).mul((float)Math.exp(-10 * step));
                velocity.fma(-velocity.dot(up), up);
                up.fma(step, velocity);
                if(up.lengthSquared() < 1.0E-8F) up.set(direction);
                up.normalize();
                remaining -= step;
            }
        }
        prevTime = time;
        return new Vector3f(up);
    }
}
