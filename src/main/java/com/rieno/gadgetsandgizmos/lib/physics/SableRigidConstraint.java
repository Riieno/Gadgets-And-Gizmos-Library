package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Keep a native fixed joint rigid while its controlled frames change
public final class SableRigidConstraint implements FixedConstraintHandle{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    final SubLevelPhysicsSystem system;
    private final FixedConstraintHandle nativeHandle;
    private final @Nullable ServerSubLevel bodyA;
    private final @Nullable ServerSubLevel bodyB;
    private final Vector3d posA;
    private final Vector3d posB;
    private final Quaterniond frameA;
    private final Quaterniond frameB = new Quaterniond();
    private final Quaterniond prevOrientation;
    private final Vector3d prevParentAnchor;
    private final Vector3d prevChildAnchor;
    final Vector3d relativeAngularVelocity = new Vector3d();
    final Vector3d parentAnchorVelocity = new Vector3d();
    final Vector3d childAnchorVelocity = new Vector3d();
    private boolean removed;

    // Retain the native joint and its original attachment frames
    private SableRigidConstraint(SubLevelPhysicsSystem system, FixedConstraintHandle handle,
            @Nullable ServerSubLevel bodyA, @Nullable ServerSubLevel bodyB,
            Vector3dc posA, Vector3dc posB, Quaterniondc orientation){
        this.system = system;
        this.nativeHandle = handle;
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.posA = new Vector3d(posA);
        this.posB = new Vector3d(posB);
        this.frameA = new Quaterniond(orientation).normalize();
        this.prevOrientation = relativeOrientation();
        this.prevParentAnchor = new Vector3d(parentAnchor());
        this.prevChildAnchor = new Vector3d(childAnchor());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create a native Rapier fixed joint whose locked frames remain exact after every substep
    public static @Nullable SableRigidConstraint attach(ServerLevel level,
            @Nullable ServerSubLevel bodyA, @Nullable ServerSubLevel bodyB,
            Vector3dc posA, Vector3dc posB, Quaterniondc orientation) throws ReflectiveOperationException{
        if(level == null || bodyA == bodyB || bodyA != null && (bodyA.isRemoved() || bodyA.getLevel() != level)
                || bodyB != null && (bodyB.isRemoved() || bodyB.getLevel() != level)) return null;
        validateFrame(posA, orientation);
        validateFrame(posB, new Quaterniond());
        var container = SubLevelContainer.getContainer(level);
        if(container == null) return null;
        var system = container.physicsSystem();
        if(!SableRigidConstraintEvents.canAttach(system, bodyB == null ? null : bodyA, bodyB == null ? bodyA : bodyB)) return null;
        Object created = SableConstraintApi.addConstraint(system.getPipeline(), bodyA, bodyB,
                SableConstraintApi.fixedConfiguration(posA, posB, orientation));
        if(!(created instanceof FixedConstraintHandle handle)){
            if(created instanceof PhysicsConstraintHandle other) other.remove();
            return null;
        }
        handle.setContactsEnabled(false);
        var res = new SableRigidConstraint(system, handle, bodyA, bodyB, posA, posB, orientation);
        SableRigidConstraintEvents.retain(res);
        return res;
    }

    // Update one original attachment frame without adding spring motors
    public void setFrame(int frame, Vector3dc pos, Quaterniondc orientation){
        if(removed) throw new IllegalStateException("Constraint is unavailable");
        if(frame < 1 || frame > 2) throw new IllegalArgumentException("Frame must be 1 or 2");
        validateFrame(pos, orientation);
        if(frame == 1){
            posA.set(pos);
            frameA.set(orientation).normalize();
        }else{
            posB.set(pos);
            frameB.set(orientation).normalize();
        }
    }

    // Read the impulses produced by the native fixed joint
    @Override public void getJointImpulses(Vector3d linear, Vector3d angular){ nativeHandle.getJointImpulses(linear, angular); }

    // Set contacts on the native joint
    @Override public void setContactsEnabled(boolean enabled){ nativeHandle.setContactsEnabled(enabled); }

    // Preserve the native handle contract while every degree of freedom remains locked
    @Override public void setMotor(ConstraintJointAxis axis, double target, double stiffness, double damping,
            boolean hasMaxForce, double maxForce){ nativeHandle.setMotor(axis, target, stiffness, damping, hasMaxForce, maxForce); }

    // Stop correcting this attachment before releasing its native handle
    @Override public void remove(){
        if(removed) return;
        removed = true;
        SableRigidConstraintEvents.release(this);
        if(nativeHandle.isValid()) nativeHandle.remove();
    }

    // Treat removed bodies as unavailable before calling the native handle
    @Override public boolean isValid(){
        return !removed && (bodyA == null || !bodyA.isRemoved()) && (bodyB == null || !bodyB.isRemoved()) && nativeHandle.isValid();
    }

    // Normalize the attachment as a parent and one moving child
    @Nullable ServerSubLevel parent(){ return bodyB == null ? null : bodyA; }
    ServerSubLevel child(){ return bodyB == null ? bodyA : bodyB; }
    Vector3dc parentAnchor(){ return bodyB == null ? posB : posA; }
    Vector3dc childAnchor(){ return bodyB == null ? posA : posB; }
    Quaterniond relativeOrientation(){
        return bodyB == null ? new Quaterniond(frameB).mul(new Quaterniond(frameA).conjugate()).normalize()
                : new Quaterniond(frameA).mul(new Quaterniond(frameB).conjugate()).normalize();
    }

    // Refresh native frames and retain only the angular and linear motion requested by the caller
    void prepare(double timeStep) throws ReflectiveOperationException{
        SableConstraintApi.setFrame(nativeHandle, 1, posA, frameA);
        SableConstraintApi.setFrame(nativeHandle, 2, posB, frameB);
        Quaterniond relative = relativeOrientation();
        Quaterniond delta = new Quaterniond(relative).mul(new Quaterniond(prevOrientation).conjugate()).normalize();
        if(delta.w < 0.0D) delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        double length = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        relativeAngularVelocity.set(delta.x, delta.y, delta.z);
        if(length > 1.0E-12D) relativeAngularVelocity.mul(2.0D * Math.atan2(length, delta.w) / (length * timeStep));
        else relativeAngularVelocity.zero();
        parentAnchorVelocity.set(parentAnchor()).sub(prevParentAnchor).div(timeStep);
        childAnchorVelocity.set(childAnchor()).sub(prevChildAnchor).div(timeStep);
        prevOrientation.set(relative);
        prevParentAnchor.set(parentAnchor());
        prevChildAnchor.set(childAnchor());
    }

    // Reject invalid frames before they enter the native solver
    private static void validateFrame(Vector3dc pos, Quaterniondc orientation){
        if(pos == null || orientation == null || !pos.isFinite() || !orientation.isFinite()
                || !Double.isFinite(orientation.lengthSquared()) || orientation.lengthSquared() < 1.0E-20D){
            throw new IllegalArgumentException("A finite position and rotation are required");
        }
    }
}
