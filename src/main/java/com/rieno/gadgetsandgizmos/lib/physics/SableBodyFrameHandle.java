package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Objects;

// Convert controller-frame impulses into physical body axes while preserving world velocity samples
public final class SableBodyFrameHandle extends RigidBodyHandle{
    private final RigidBodyHandle delegate;
    private final ScmOrientation frame;

    public SableBodyFrameHandle(RigidBodyHandle delegate, ScmOrientation frame){
        super(null, null);
        this.delegate = Objects.requireNonNull(delegate);
        this.frame = Objects.requireNonNull(frame);
    }

    @Override public boolean isValid(){ return delegate.isValid(); }
    @Override public Vector3dc getLinearVelocity(){ return delegate.getLinearVelocity(); }
    @Override public Vector3dc getAngularVelocity(){ return delegate.getAngularVelocity(); }
    @Override public Vector3d getLinearVelocity(Vector3d dest){ return delegate.getLinearVelocity(dest); }
    @Override public Vector3d getAngularVelocity(Vector3d dest){ return delegate.getAngularVelocity(dest); }
    @Override public void applyLinearImpulse(Vector3dc val){ delegate.applyLinearImpulse(physical(val)); }
    @Override public void applyAngularImpulse(Vector3dc val){ delegate.applyAngularImpulse(physical(val)); }
    @Override public void applyTorqueImpulse(Vector3dc val){ delegate.applyTorqueImpulse(physical(val)); }
    @Override public void applyLinearAndAngularImpulse(Vector3dc force, Vector3dc torque){
        delegate.applyLinearAndAngularImpulse(physical(force), physical(torque));
    }
    @Override public void applyLinearAndAngularImpulse(Vector3dc force, Vector3dc torque, boolean wake){
        delegate.applyLinearAndAngularImpulse(physical(force), physical(torque), wake);
    }
    @Override public void applyImpulseAtPoint(Vector3dc impulse, Vector3dc point){ delegate.applyImpulseAtPoint(physical(impulse), point); }
    @Override public void applyImpulseAtPoint(Vec3 impulse, Vec3 point){
        Vec3 val = frame.toBody(impulse);
        delegate.applyImpulseAtPoint(val, point);
    }
    @Override public void applyForcesAndReset(ForceTotal total){ total.applyForces(this); total.reset(); }
    @Override public void addLinearAndAngularVelocity(Vector3dc force, Vector3dc torque){
        throw new IllegalStateException("Controller-frame handles accept impulses, not velocity changes");
    }
    @Override public void teleport(Vector3dc pos, Quaterniondc rotation){
        throw new IllegalStateException("Controller-frame handles cannot teleport a physical body");
    }

    private Vector3d physical(Vector3dc val){ return frame.rotation().transform(val, new Vector3d()); }
}
