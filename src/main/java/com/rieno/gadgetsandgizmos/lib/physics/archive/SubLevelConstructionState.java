package com.rieno.gadgetsandgizmos.lib.physics.archive;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Protect unfinished native bodies from empty-plot removal, splitting and block entity ticks
public final class SubLevelConstructionState{
    private static final Map<ServerSubLevel, State> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private SubLevelConstructionState(){}

    // Retain a body's intended pose until construction finishes
    static void retain(ServerSubLevel body, Pose3d pose){ ACTIVE.put(body, new State(new Pose3d(pose), new WaitingMass(new Vector3d(pose.rotationPoint())))); }
    // Restore normal native body behavior after construction or rollback
    static void release(ServerSubLevel body){ ACTIVE.remove(body); }
    // Report whether native lifecycle callbacks must preserve this unfinished body
    public static boolean isBuilding(ServerSubLevel body){ return ACTIVE.containsKey(body); }
    // Preserve world placement while native mass moves the body's rotation point
    public static Pose3d pose(ServerSubLevel body){
        State saved = ACTIVE.get(body);
        if(saved == null) return null;
        Pose3d pose = new Pose3d(saved.pose());
        var mass = body.getMassTracker();
        if(mass != null && !mass.isInvalid() && mass.getCenterOfMass() != null){
            var center = mass.getCenterOfMass();
            Vector3d shift = new Vector3d(center).sub(pose.rotationPoint());
            pose.position().add(pose.orientation().transform(shift));
            pose.rotationPoint().set(center);
        }
        return pose;
    }

    // Supply finite physics data until the first physical block supplies native mass
    public static MassData mass(ServerSubLevel body, MassData original){
        State state = ACTIVE.get(body);
        return state != null && (original == null || original.isInvalid()) ? state.mass() : original;
    }

    // Retain a stable local rotation point without adding placeholder world blocks
    private record State(Pose3d pose, MassData mass){}

    // Keep an empty physics body valid while it waits for its first scheduled layer
    private record WaitingMass(Vector3dc center) implements MassData{
        @Override public double getMass(){ return 1; }
        @Override public double getInverseMass(){ return 1; }
        @Override public Matrix3dc getInertiaTensor(){ return new Matrix3d(); }
        @Override public Matrix3dc getInverseInertiaTensor(){ return new Matrix3d(); }
        @Override public Vector3dc getCenterOfMass(){ return center; }
    }
}
