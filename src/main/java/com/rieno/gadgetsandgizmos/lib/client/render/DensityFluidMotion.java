package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

// Integrate container acceleration and rotation into a damped, inertial fluid surface
public final class DensityFluidMotion{
    public record Frame(float upX, float upY, float upZ, float waveX, float waveZ, float mixing){
        public Vector3f up(){ return new Vector3f(upX, upY, upZ); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private FluidSurfaceMotion surface = new FluidSurfaceMotion();
    private final Vector3f prevVelocity = new Vector3f(), prevAngular = new Vector3f(), acceleration = new Vector3f();
    private FluidMotionInput sampled, stepped;
    private Frame prev, current, rendered;
    private double stepTime = Double.NaN;
    private double lastMotionTime;
    private float waveX, waveZ, velocityX, velocityZ, mixing;
    private boolean sleeping;
    private long steps;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public Frame update(Vector3fc target, Vector3fc position, double time){
        return update(target, position, null, time);
    }

    // Retain the vector-based API for callers already supplying container positions
    public Frame update(Vector3fc target, Vector3fc position, Quaternionfc orientation, double time){
        var up = new Vector3f(target);
        if(!up.isFinite() || up.lengthSquared() < 1.0E-8F) up.set(0, 1, 0);
        up.normalize();
        var pos = new Vector3f(position);
        if(!pos.isFinite()) pos.zero();
        var rotation = orientation == null ? new Quaternionf() : new Quaternionf(orientation);
        if(!Float.isFinite(rotation.x + rotation.y + rotation.z + rotation.w) || rotation.lengthSquared() < 1.0E-8F) rotation.identity();
        rotation.normalize();
        return update(new FluidMotionInput(pos.x, pos.y, pos.z, up.x, up.y, up.z,
                rotation.x, rotation.y, rotation.z, rotation.w, Double.isFinite(time) ? time : 0));
    }

    // Differentiate world positions before converting acceleration into the bottle's coordinates
    public Frame update(FluidMotionInput input){
        if(sampled == null || input.time() < sampled.time() || input.time() - sampled.time() > 10){
            reset(input);
            return rendered;
        }
        if(input.time() == sampled.time()) return rendered;
        var last = sampled;
        float rest = restProgress();
        sampled = input;
        if(!unchanged(input, last)){
            if(rest > 0 && rest < 1){
                float remaining = 1 - rest * rest * (3 - 2 * rest);
                prev = current = rendered;
                waveX = rendered.waveX; waveZ = rendered.waveZ; mixing = rendered.mixing;
                velocityX *= remaining; velocityZ *= remaining; acceleration.mul(remaining);
                surface = new FluidSurfaceMotion();
                surface.update(rendered.up(), stepTime);
            }
            lastMotionTime = input.time();
        }
        if(sleeping && unchanged(input, stepped)){
            stepTime = input.time();
            return rendered;
        }
        sleeping = false;
        int count = 0;
        while(input.time() - stepTime >= 1 && count++ < 4){
            stepTime += 1;
            step(interpolate(last, input, stepTime));
        }
        if(input.time() - stepTime >= 1) stepTime = input.time();
        if(sleeping) return rendered = current;
        float alpha = (float)Math.clamp(input.time() - stepTime, 0, 1);
        rendered = new Frame(lerp(prev.upX, current.upX, alpha), lerp(prev.upY, current.upY, alpha),
                lerp(prev.upZ, current.upZ, alpha), lerp(prev.waveX, current.waveX, alpha),
                lerp(prev.waveZ, current.waveZ, alpha), lerp(prev.mixing, current.mixing, alpha));
        return rendered = settle(rendered, input);
    }

    // Return zero during container movement and one after twenty ticks without movement
    public float restProgress(){
        return sampled == null ? 1 : (float)Math.clamp((sampled.time() - lastMotionTime) / 20, 0, 1);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    long steps(){ return steps; }

    private void reset(FluidMotionInput input){
        sampled = stepped = input;
        stepTime = input.time();
        lastMotionTime = input.time() - 20;
        surface = new FluidSurfaceMotion();
        surface.update(input.up(), stepTime);
        prevVelocity.zero(); prevAngular.zero(); acceleration.zero();
        waveX = waveZ = velocityX = velocityZ = mixing = 0;
        prev = current = rendered = new Frame(input.upX(), input.upY(), input.upZ(), 0, 0, 0);
        sleeping = true;
    }

    private void step(FluidMotionInput input){
        steps++;
        prev = current;
        var rotation = input.rotation();
        var travel = new Vector3f((float)(input.x() - stepped.x()), (float)(input.y() - stepped.y()), (float)(input.z() - stepped.z()));
        if(travel.lengthSquared() > 4){ reset(input); return; }
        var velocity = new Vector3f(travel).mul(20);
        var force = new Vector3f(velocity).sub(prevVelocity).mul(20);
        limit(force, 60);
        rotation.transformInverse(force).mul(1F / 9.81F);
        acceleration.lerp(force, .4F);
        var delta = stepped.rotation().conjugate().mul(rotation).normalize();
        if(delta.w < 0) delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        float length = (float)Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        var angular = new Vector3f(delta.x, delta.y, delta.z);
        if(length > 1.0E-6F) angular.mul((float)(2 * Math.atan2(length, delta.w)) * 20 / length);
        else angular.zero();
        limit(angular, 10);
        var angularForce = new Vector3f(angular).sub(prevAngular).mul(20).cross(new Vector3f(.02F, .06F, .2F)).mul(.025F);
        limit(angularForce, 1.5F);
        var effectiveUp = input.up().fma(.65F, acceleration).add(angularForce);
        if(effectiveUp.lengthSquared() < 1.0E-8F) effectiveUp.set(input.up());
        effectiveUp.normalize();
        var up = surface.update(effectiveUp, stepTime);
        float forceX = -acceleration.x * 15 - angularForce.x * 10 + angular.y * 1.2F + angular.z * .5F;
        float forceZ = -acceleration.z * 15 - angularForce.z * 10 + angular.x * .8F - angular.y * .9F;
        velocityX += (Math.clamp(forceX, -24, 24) - waveX * 24 - velocityX * 5.2F) * .05F;
        velocityZ += (Math.clamp(forceZ, -24, 24) - waveZ * 19 - velocityZ * 4.8F) * .05F;
        waveX = Math.clamp(waveX + velocityX * .05F, -1, 1);
        waveZ = Math.clamp(waveZ + velocityZ * .05F, -1, 1);
        float excitation = Math.min(2, acceleration.length() + angularForce.length()) * .18F + angular.length() * .045F;
        mixing = Math.min(.98F, mixing * .78F + excitation);
        sleeping = unchanged(input, stepped) && (restProgress() >= 1 || up.distanceSquared(input.up()) < 1.0E-7F
                && acceleration.lengthSquared() < 1.0E-7F
                && Math.abs(waveX) + Math.abs(waveZ) + Math.abs(velocityX) + Math.abs(velocityZ) + mixing < .002F);
        prevVelocity.set(velocity);
        prevAngular.set(angular);
        stepped = input;
        if(sleeping){
            waveX = waveZ = velocityX = velocityZ = mixing = 0;
            up.set(input.up());
        }
        current = new Frame(up.x, up.y, up.z, waveX, waveZ, mixing);
    }

    private static FluidMotionInput interpolate(FluidMotionInput from, FluidMotionInput to, double time){
        float alpha = (float)Math.clamp((time - from.time()) / (to.time() - from.time()), 0, 1);
        var rotation = from.rotation().slerp(to.rotation(), alpha);
        var up = from.up().lerp(to.up(), alpha).normalize();
        return new FluidMotionInput(from.x() + (to.x() - from.x()) * alpha, from.y() + (to.y() - from.y()) * alpha,
                from.z() + (to.z() - from.z()) * alpha, up.x, up.y, up.z, rotation.x, rotation.y, rotation.z, rotation.w, time);
    }

    // Ease residual tilt and waves to rest over the same second as the dye colours
    private Frame settle(Frame frame, FluidMotionInput input){
        float progress = restProgress();
        float alpha = progress * progress * (3 - 2 * progress);
        var up = frame.up().lerp(input.up(), alpha);
        if(up.lengthSquared() < 1.0E-8F) up.set(input.up());
        up.normalize();
        return new Frame(up.x, up.y, up.z, frame.waveX * (1 - alpha), frame.waveZ * (1 - alpha), frame.mixing * (1 - alpha));
    }

    private static boolean unchanged(FluidMotionInput a, FluidMotionInput b){
        double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
        double same = square(a.rotationX() - b.rotationX()) + square(a.rotationY() - b.rotationY())
                + square(a.rotationZ() - b.rotationZ()) + square(a.rotationW() - b.rotationW());
        double opposite = square(a.rotationX() + b.rotationX()) + square(a.rotationY() + b.rotationY())
                + square(a.rotationZ() + b.rotationZ()) + square(a.rotationW() + b.rotationW());
        return x * x + y * y + z * z < 1.0E-12D && Math.min(same, opposite) < 1.0E-12D
                && a.up().distanceSquared(b.up()) < 1.0E-8F;
    }

    private static void limit(Vector3f val, float maximum){ if(val.lengthSquared() > maximum * maximum) val.normalize().mul(maximum); }
    private static double square(float val){ return (double)val * val; }
    private static float lerp(float from, float to, float alpha){ return from + (to - from) * alpha; }
}
