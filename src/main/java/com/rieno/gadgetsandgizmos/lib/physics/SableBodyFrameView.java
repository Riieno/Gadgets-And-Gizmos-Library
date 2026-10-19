package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Objects;
import java.util.UUID;

// Present configured craft axes while retaining the body's real block positions and mass
public final class SableBodyFrameView{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final SubLevel body;
    private final ScmOrientation frame;


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public SableBodyFrameView(SubLevel body, ScmOrientation frame){
        this.body = Objects.requireNonNull(body);
        this.frame = Objects.requireNonNull(frame);
    }

    public SubLevel body(){ return body; }
    public ScmOrientation frame(){ return frame; }
    public Level getLevel(){ return body.getLevel(); }
    public UUID getUniqueId(){ return body.getUniqueId(); }
    public FramePose logicalPose(){ return new FramePose(body.logicalPose(), frame); }
    public FramePose renderPose(){
        return new FramePose(body instanceof ClientSubLevel client ? client.renderPose() : body.logicalPose(), frame);
    }

    public FrameMass getMassTracker(){
        if(!(body instanceof ServerSubLevel server)) throw new IllegalStateException("Mass is unavailable on a client body");
        return new FrameMass(server.getMassTracker(), frame);
    }

    public QueuedForceGroup getOrCreateQueuedForceGroup(ForceGroup group){
        if(!(body instanceof ServerSubLevel server)) throw new IllegalStateException("Forces require a server body");
        return server.getOrCreateQueuedForceGroup(group);
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rotate direction sampling without moving any block or projector origin
    public static final class FramePose{
        private final Pose3dc pose;
        private final ScmOrientation frame;

        public FramePose(Pose3dc pose, ScmOrientation frame){
            this.pose = Objects.requireNonNull(pose);
            this.frame = Objects.requireNonNull(frame);
        }

        public Vector3dc position(){ return pose.position(); }
        public Quaterniondc orientation(){ return new Quaterniond(pose.orientation()).mul(frame.rotation()); }
        public Vector3dc rotationPoint(){ return pose.rotationPoint(); }
        public Vector3dc scale(){ return pose.scale(); }
        public Vector3d transformPosition(Vector3dc val, Vector3d dest){ return pose.transformPosition(val, dest); }
        public Vector3d transformPosition(Vector3d val){ return pose.transformPosition(val); }
        public Vector3d transformPositionInverse(Vector3dc val, Vector3d dest){ return pose.transformPositionInverse(val, dest); }
        public Vector3d transformPositionInverse(Vector3d val){ return pose.transformPositionInverse(val); }
    }

    // Express inertia in the same axes as the configured control frame
    public static final class FrameMass implements MassData{
        private final MassData mass;
        private final ScmOrientation frame;

        public FrameMass(MassData mass, ScmOrientation frame){
            this.mass = Objects.requireNonNull(mass);
            this.frame = Objects.requireNonNull(frame);
        }

        @Override public double getMass(){ return mass.getMass(); }
        @Override public double getInverseMass(){ return mass.getInverseMass(); }
        @Override public Vector3dc getCenterOfMass(){ return mass.getCenterOfMass(); }
        @Override public Matrix3dc getInertiaTensor(){ return rotate(mass.getInertiaTensor()); }
        @Override public Matrix3dc getInverseInertiaTensor(){ return rotate(mass.getInverseInertiaTensor()); }
        public FrameMass getSelfMassTracker(){ return this; }

        private Matrix3d rotate(Matrix3dc val){
            return frame.fromBodyTensor(val);
        }
    }
}
