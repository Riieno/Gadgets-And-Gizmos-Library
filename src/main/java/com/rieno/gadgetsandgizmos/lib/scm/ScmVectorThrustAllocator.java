package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Solve steering direction and throttle together inside each engine's physical cone
public final class ScmVectorThrustAllocator{
    private ScmVectorThrustAllocator(){}

    public record Result(List<Vec3> forces, double residual){
        public Result{ forces = List.copyOf(forces); }
    }

    public static Result allocate(List<ScmPrecisionAllocator.Unit> units, Vec3 force, Vec3 torque, boolean prioritizeTorque){
        List<Vec3> forces = new ArrayList<>(Collections.nCopies(units.size(), Vec3.ZERO));
        double lever = Math.max(1.0D, units.stream().mapToDouble(unit -> unit.momentArm().length()).max().orElse(1.0D));
        double torqueWeight = (prioritizeTorque ? 4.0D : 1.0D) / (lever * lever);
        Vec3 remainingForce = force;
        Vec3 remainingTorque = torque;
        double scale = Math.max(1.0D, force.lengthSqr() + torque.lengthSqr() * torqueWeight);
        for(int pass = 0; pass < 256; pass++){
            double change = 0.0D;
            for(int idx = 0; idx < units.size(); idx++){
                var unit = units.get(idx);
                Vec3 prev = forces.get(idx);
                Vec3 arm = unit.momentArm();
                Vec3 gradient = remainingForce.add(remainingTorque.cross(arm).scale(torqueWeight));
                double step = 1.0D / (1.0D + arm.lengthSqr() * torqueWeight);
                Vec3 next = project(prev.add(gradient.scale(step)), unit.force(), unit.coneDegrees());
                Vec3 delta = next.subtract(prev);
                forces.set(idx, next);
                remainingForce = remainingForce.subtract(delta);
                remainingTorque = remainingTorque.subtract(arm.cross(delta));
                change += delta.lengthSqr();
            }
            if(change <= scale * 1.0E-14D) break;
        }
        double residual = Math.sqrt((remainingForce.lengthSqr() + remainingTorque.lengthSqr() * torqueWeight) / scale);
        return new Result(forces, residual);
    }

    // Project to a forward-only cone and its finite thrust cap without inventing lateral authority
    public static Vec3 project(Vec3 force, Vec3 nominal, double coneDegrees){
        double capacity = nominal.length();
        if(capacity <= 1.0E-9D) return Vec3.ZERO;
        Vec3 axis = nominal.scale(1.0D / capacity);
        double axial = force.dot(axis);
        Vec3 lateral = force.subtract(axis.scale(axial));
        double radial = lateral.length();
        double angle = Math.toRadians(ScmThrustGeometry.coneDegrees(coneDegrees));
        if(angle <= 1.0E-9D) return axis.scale(Math.clamp(axial, 0.0D, capacity));
        Vec3 projected = force;
        if(axial < 0.0D || radial > axial * Math.tan(angle)){
            Vec3 boundary = axis.scale(Math.cos(angle));
            if(radial > 1.0E-12D) boundary = boundary.add(lateral.scale(Math.sin(angle) / radial));
            projected = boundary.scale(Math.max(0.0D, axial * Math.cos(angle) + radial * Math.sin(angle)));
        }
        double magnitude = projected.length();
        return magnitude > capacity ? projected.scale(capacity / magnitude) : projected;
    }
}
