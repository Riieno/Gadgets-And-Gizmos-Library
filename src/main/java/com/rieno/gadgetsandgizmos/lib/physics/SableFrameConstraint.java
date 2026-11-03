package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// Retain one solver joint as an attachment moves without rewriting either body's pose
public final class SableFrameConstraint implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Release managed joints before their native scene or either body disappears
    private static final Map<PhysicsPipeline, Set<SableFrameConstraint>> RETAINED = new IdentityHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain native ownership and the last applied attachment frames
    private @Nullable PhysicsPipeline pipeline;
    private @Nullable PhysicsPipelineBody parent;
    private @Nullable PhysicsPipelineBody child;
    private @Nullable GenericConstraintHandle handle;
    private final Vector3d parentAnchor = new Vector3d();
    private final Vector3d childAnchor = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update frames in place and wake bodies only when their target changes
    public boolean update(PhysicsPipeline pipeline, @Nullable PhysicsPipelineBody parent,
            @Nullable PhysicsPipelineBody child, Vector3dc parentAnchor, Vector3dc childAnchor,
            Quaterniondc orientation){
        if(pipeline == null || parent == child || parent != null && parent.isRemoved()
                || child != null && child.isRemoved() || parentAnchor == null || childAnchor == null
                || orientation == null || !parentAnchor.isFinite() || !childAnchor.isFinite()
                || !orientation.isFinite() || !Double.isFinite(orientation.lengthSquared())
                || orientation.lengthSquared() < 1.0E-20D){
            close();
            return false;
        }
        Quaterniond rotation = new Quaterniond(orientation).normalize();
        if(this.pipeline != pipeline || this.parent != parent || this.child != child || !isValid()){
            close();
            handle = SableConstraintApi.fixedFrameConstraint(pipeline, parent, child,
                    parentAnchor, childAnchor, rotation);
            if(handle == null) return false;
            this.pipeline = pipeline;
            this.parent = parent;
            this.child = child;
            RETAINED.computeIfAbsent(pipeline, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
            this.parentAnchor.set(parentAnchor);
            this.childAnchor.set(childAnchor);
            this.orientation.set(rotation);
            wakeBodies();
            return true;
        }
        boolean parentChanged = this.parentAnchor.distanceSquared(parentAnchor) > 1.0E-8D
                || Math.abs(this.orientation.dot(rotation)) < 1.0D - 1.0E-12D;
        boolean childChanged = this.childAnchor.distanceSquared(childAnchor) > 1.0E-8D;
        if(parentChanged) handle.setFrame1(parentAnchor, rotation);
        if(childChanged) handle.setFrame2(childAnchor, new Quaterniond());
        if(parentChanged || childChanged){
            this.parentAnchor.set(parentAnchor);
            this.childAnchor.set(childAnchor);
            this.orientation.set(rotation);
            wakeBodies();
        }
        return true;
    }

    // Check availability before retaining a stationary or temporarily unavailable path
    public boolean isValid(){
        return handle != null && (parent == null || !parent.isRemoved())
                && (child == null || !child.isRemoved()) && handle.isValid();
    }

    // Release the native joint before its body or level is unloaded
    @Override public void close(){
        GenericConstraintHandle prev = handle;
        if(pipeline != null){
            Set<SableFrameConstraint> joints = RETAINED.get(pipeline);
            if(joints != null){
                joints.remove(this);
                if(joints.isEmpty()) RETAINED.remove(pipeline);
            }
        }
        handle = null;
        pipeline = null;
        parent = null;
        child = null;
        if(prev != null && prev.isValid()) prev.remove();
    }

    // Close attachments while the native body is still present in its scene
    public static void releaseBody(PhysicsPipeline pipeline, PhysicsPipelineBody body){
        Set<SableFrameConstraint> joints = RETAINED.get(pipeline);
        if(joints == null) return;
        for(SableFrameConstraint joint : new ArrayList<>(joints)){
            if(joint.parent == body || joint.child == body) joint.close();
        }
    }

    // Drop all native handles before disposing their owning physics scene
    public static void releasePipeline(PhysicsPipeline pipeline){
        Set<SableFrameConstraint> joints = RETAINED.get(pipeline);
        if(joints == null) return;
        for(SableFrameConstraint joint : new ArrayList<>(joints)) joint.close();
    }

    // Let stationary assemblies sleep between actual target changes
    private void wakeBodies(){
        if(parent != null) pipeline.wakeUp(parent);
        if(child != null) pipeline.wakeUp(child);
    }
}
