package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Objects;

// Capture a controller's requested impulses without applying a second physical control output
public final class SableImpulseCapture extends RigidBodyHandle{
    private final RigidBodyHandle delegate;
    private final Vector3d center;
    private final Vector3d linear = new Vector3d();
    private final Vector3d angular = new Vector3d();

    public SableImpulseCapture(RigidBodyHandle delegate, Vector3dc center){
        super(null, null);
        this.delegate = Objects.requireNonNull(delegate);
        this.center = new Vector3d(center);
    }

    @Override public boolean isValid(){ return delegate.isValid(); }
    @Override public Vector3dc getLinearVelocity(){ return delegate.getLinearVelocity(); }
    @Override public Vector3dc getAngularVelocity(){ return delegate.getAngularVelocity(); }
    @Override public Vector3d getLinearVelocity(Vector3d dest){ return delegate.getLinearVelocity(dest); }
    @Override public Vector3d getAngularVelocity(Vector3d dest){ return delegate.getAngularVelocity(dest); }
    @Override public void applyLinearImpulse(Vector3dc val){ linear.add(val); }
    @Override public void applyAngularImpulse(Vector3dc val){ angular.add(val); }
    @Override public void applyTorqueImpulse(Vector3dc val){ angular.add(val); }
    @Override public void applyLinearAndAngularImpulse(Vector3dc force, Vector3dc torque){ linear.add(force); angular.add(torque); }
    @Override public void applyLinearAndAngularImpulse(Vector3dc force, Vector3dc torque, boolean val){ applyLinearAndAngularImpulse(force, torque); }
    @Override public void applyImpulseAtPoint(Vector3dc impulse, Vector3dc point){
        linear.add(impulse);
        angular.add(new Vector3d(point).sub(center).cross(impulse));
    }
    @Override public void applyImpulseAtPoint(Vec3 impulse, Vec3 point){
        applyImpulseAtPoint(new Vector3d(impulse.x, impulse.y, impulse.z), new Vector3d(point.x, point.y, point.z));
    }
    @Override public void applyForcesAndReset(ForceTotal total){ total.applyForces(this); total.reset(); }
    @Override public void addLinearAndAngularVelocity(Vector3dc force, Vector3dc torque){
        throw new IllegalStateException("A captured controller cannot change body velocity directly");
    }
    @Override public void teleport(Vector3dc pos, Quaterniondc rotation){
        throw new IllegalStateException("A captured controller cannot teleport its body");
    }

    // Convert this substep's body-space impulses into physical force and torque
    public Snapshot snapshot(double dt){
        if(!Double.isFinite(dt) || dt <= 0) throw new IllegalArgumentException("Invalid capture timestep");
        return new Snapshot(new Vec3(linear.x / dt, linear.y / dt, linear.z / dt),
                new Vec3(angular.x / dt, angular.y / dt, angular.z / dt));
    }

    public record Snapshot(Vec3 force, Vec3 torque){ }
}
