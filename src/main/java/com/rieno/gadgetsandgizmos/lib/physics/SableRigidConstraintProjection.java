package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Project each attachment tree without discarding its load reaction or momentum
final class SableRigidConstraintProjection{
    private SableRigidConstraintProjection(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Correct connected bodies together so sibling and nested heads share one rigid frame
    static void project(SubLevelPhysicsSystem system, List<SableRigidConstraint> joints){
        if(joints.isEmpty()) return;
        Map<ServerSubLevel, SableRigidConstraint> incoming = new IdentityHashMap<>();
        Map<ServerSubLevel, List<SableRigidConstraint>> outgoing = new IdentityHashMap<>();
        for(SableRigidConstraint joint : joints){
            incoming.put(joint.child(), joint);
            outgoing.computeIfAbsent(joint.parent(), ignored -> new ArrayList<>()).add(joint);
        }
        Set<ServerSubLevel> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        for(SableRigidConstraint joint : joints){
            ServerSubLevel root = joint.child();
            while(incoming.containsKey(root) && incoming.get(root).parent() != null) root = incoming.get(root).parent();
            if(!roots.add(root)) continue;
            List<BodyFrame> frames = new ArrayList<>();
            BodyFrame rootFrame = new BodyFrame(system, root);
            SableRigidConstraint rootJoint = incoming.get(root);
            boolean anchored = rootJoint != null && rootJoint.parent() == null;
            if(anchored) rootFrame.attach(null, rootJoint);
            gather(system, rootFrame, outgoing, frames);
            if(frames.stream().anyMatch(frame -> !frame.isValid())) continue;
            if(!anchored && !preserveMomentum(frames)) continue;
            for(BodyFrame frame : frames) frame.apply(system);
        }
    }

    // Resolve all target poses and intentional velocities from the same corrected parent frames
    private static void gather(SubLevelPhysicsSystem system, BodyFrame parent,
            Map<ServerSubLevel, List<SableRigidConstraint>> outgoing, List<BodyFrame> frames){
        frames.add(parent);
        for(SableRigidConstraint joint : outgoing.getOrDefault(parent.body, List.of())){
            BodyFrame child = new BodyFrame(system, joint.child());
            child.attach(parent, joint);
            gather(system, child, outgoing, frames);
        }
    }

    // Preserve combined center of mass and linear and angular momentum for freely moving assemblies
    private static boolean preserveMomentum(List<BodyFrame> frames){
        double mass = 0.0D;
        Vector3d center = new Vector3d();
        Vector3d targetCenter = new Vector3d();
        Vector3d momentum = new Vector3d();
        Vector3d controlledVelocity = new Vector3d();
        for(BodyFrame frame : frames){
            mass += frame.mass;
            center.fma(frame.mass, frame.pos);
            targetCenter.fma(frame.mass, frame.targetPos);
            momentum.fma(frame.mass, frame.linearVelocity);
            controlledVelocity.fma(frame.mass, frame.controlledVelocity);
        }
        center.div(mass);
        targetCenter.div(mass);
        Vector3d shift = new Vector3d(center).sub(targetCenter);
        Matrix3d inertia = new Matrix3d().zero();
        Vector3d angularMomentum = new Vector3d();
        Vector3d controlledMomentum = new Vector3d();
        for(BodyFrame frame : frames){
            frame.targetPos.add(shift);
            Vector3d offset = new Vector3d(frame.pos).sub(center);
            Vector3d targetOffset = new Vector3d(frame.targetPos).sub(center);
            Matrix3d currentInertia = frame.worldInertia(frame.orientation);
            Matrix3d targetInertia = frame.worldInertia(frame.targetOrientation);
            angularMomentum.add(currentInertia.transform(new Vector3d(frame.angularVelocity)));
            angularMomentum.add(offset.cross(new Vector3d(frame.linearVelocity).mul(frame.mass)));
            controlledMomentum.add(targetInertia.transform(new Vector3d(frame.controlledAngularVelocity)));
            controlledMomentum.add(new Vector3d(targetOffset).cross(new Vector3d(frame.controlledVelocity).mul(frame.mass)));
            inertia.add(targetInertia).add(parallelInertia(targetOffset, frame.mass));
        }
        Vector3d rotation = inertia.invert().transform(angularMomentum.sub(controlledMomentum));
        Vector3d translation = momentum.sub(controlledVelocity).div(mass);
        if(!rotation.isFinite() || !translation.isFinite()) return false;
        for(BodyFrame frame : frames){
            frame.controlledVelocity.add(new Vector3d(rotation).cross(new Vector3d(frame.targetPos).sub(center))).add(translation);
            frame.controlledAngularVelocity.add(rotation);
        }
        return true;
    }

    // Translate a body's inertia to the combined center of mass
    private static Matrix3d parallelInertia(Vector3d offset, double mass){
        double x = offset.x;
        double y = offset.y;
        double z = offset.z;
        return new Matrix3d(
                mass * (y * y + z * z), -mass * x * y, -mass * x * z,
                -mass * x * y, mass * (x * x + z * z), -mass * y * z,
                -mass * x * z, -mass * y * z, mass * (x * x + y * y));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the solver's state separate from the exact attachment state
    private static final class BodyFrame{
        final ServerSubLevel body;
        final double mass;
        final Matrix3d inertia;
        final Vector3d center;
        final Vector3d pos;
        final Quaterniond orientation;
        final Vector3d linearVelocity = new Vector3d();
        final Vector3d angularVelocity = new Vector3d();
        final Vector3d targetPos;
        final Quaterniond targetOrientation;
        final Vector3d controlledVelocity = new Vector3d();
        final Vector3d controlledAngularVelocity = new Vector3d();

        // Read the pose and velocities that include this substep's forces and joint impulses
        BodyFrame(SubLevelPhysicsSystem system, ServerSubLevel body){
            this.body = body;
            var massData = body.getMassTracker();
            mass = massData == null ? 0.0D : massData.getMass();
            inertia = massData == null || massData.getInertiaTensor() == null ? new Matrix3d().zero()
                    : new Matrix3d(massData.getInertiaTensor());
            center = new Vector3d(body.logicalPose().rotationPoint());
            pos = new Vector3d(body.logicalPose().position());
            orientation = new Quaterniond(body.logicalPose().orientation()).normalize();
            targetPos = new Vector3d(pos);
            targetOrientation = new Quaterniond(orientation);
            system.getPipeline().getLinearVelocity(body, linearVelocity);
            system.getPipeline().getAngularVelocity(body, angularVelocity);
        }

        // Place the child at the exact parent anchor and retain only requested relative motion
        void attach(BodyFrame parent, SableRigidConstraint joint){
            Quaterniond parentOrientation = parent == null ? new Quaterniond() : parent.targetOrientation;
            Vector3d anchor = new Vector3d(joint.parentAnchor());
            if(parent != null) parentOrientation.transform(anchor.sub(parent.center)).add(parent.targetPos);
            targetOrientation.set(parentOrientation).mul(joint.relativeOrientation()).normalize();
            targetPos.set(joint.childAnchor()).sub(center);
            targetOrientation.transform(targetPos).negate().add(anchor);
            Vector3d relativeRotation = parentOrientation.transform(new Vector3d(joint.relativeAngularVelocity));
            controlledAngularVelocity.set(relativeRotation);
            if(parent != null){
                controlledAngularVelocity.add(parent.controlledAngularVelocity);
                controlledVelocity.set(parent.controlledVelocity).add(new Vector3d(parent.controlledAngularVelocity)
                        .cross(new Vector3d(targetPos).sub(parent.targetPos)));
            }
            controlledVelocity.add(new Vector3d(relativeRotation).cross(new Vector3d(targetPos).sub(anchor)));
            controlledVelocity.add(parentOrientation.transform(new Vector3d(joint.parentAnchorVelocity)));
            controlledVelocity.sub(targetOrientation.transform(new Vector3d(joint.childAnchorVelocity)));
        }

        // Rotate local inertia into the world frame
        Matrix3d worldInertia(Quaterniond rotation){
            Matrix3d frame = new Matrix3d().rotation(rotation);
            return new Matrix3d(frame).mul(inertia).mul(new Matrix3d(frame).transpose());
        }

        // Skip assemblies whose mass or pose is being rebuilt
        boolean isValid(){
            return Double.isFinite(mass) && mass > 0.0D && inertia.isFinite() && center.isFinite()
                    && targetPos.isFinite() && targetOrientation.isFinite()
                    && linearVelocity.isFinite() && angularVelocity.isFinite()
                    && controlledVelocity.isFinite() && controlledAngularVelocity.isFinite();
        }

        // Synchronize native and logical poses and remove only unwanted relative velocity
        void apply(SubLevelPhysicsSystem system){
            var pipeline = system.getPipeline();
            pipeline.teleport(body, targetPos, targetOrientation);
            pipeline.addLinearAndAngularVelocity(body, new Vector3d(controlledVelocity).sub(linearVelocity),
                    new Vector3d(controlledAngularVelocity).sub(angularVelocity));
            system.updatePose(body);
        }
    }
}
