package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Set;

// Reposition a detached assembly through its attachment frames before recreating its joint
public final class SableAttachmentPoseApi{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the attachment pose facade
    private SableAttachmentPoseApi(){}

    // Match anchors while retaining the child's rotation point and scale
    public static Pose3d alignedPose(@Nullable Pose3dc parent, Pose3dc child,
            Vector3dc parentAnchor, Vector3dc childAnchor, Quaterniondc orientation){
        if(child == null || parentAnchor == null || childAnchor == null || orientation == null
                || !parentAnchor.isFinite() || !childAnchor.isFinite() || !orientation.isFinite()
                || !Double.isFinite(orientation.lengthSquared()) || orientation.lengthSquared() < 1.0E-20D){
            throw new IllegalArgumentException("Finite attachment frames are required");
        }
        Pose3d res = new Pose3d(child);
        Quaterniond world = parent == null ? new Quaterniond() : new Quaterniond(parent.orientation());
        res.orientation().set(world.mul(orientation).normalize());
        Vector3d target = new Vector3d(parentAnchor);
        if(parent != null) parent.transformPosition(target);
        Vector3d current = new Vector3d(childAnchor);
        res.transformPosition(current);
        res.position().add(target.sub(current));
        return res;
    }

    // Move the remaining attachment tree after the caller releases its parent joint
    public static boolean align(ServerLevel level, @Nullable ServerSubLevel parent, ServerSubLevel child,
            Vector3dc parentAnchor, Vector3dc childAnchor, Quaterniondc orientation){
        var container = SubLevelContainer.getContainer(level);
        if(container == null || child == null || child == parent || child.isRemoved()
                || child.getLevel() != level || container.getSubLevel(child.getUniqueId()) != child
                || parent != null && (parent.isRemoved() || parent.getLevel() != level
                || container.getSubLevel(parent.getUniqueId()) != parent)) return false;
        Pose3d prev = new Pose3d(child.logicalPose());
        Pose3d next = alignedPose(parent == null ? null : parent.logicalPose(), prev,
                parentAnchor, childAnchor, orientation);
        if(!next.position().isFinite() || !next.orientation().isFinite()) return false;
        Quaterniond delta = new Quaterniond(next.orientation()).mul(new Quaterniond(prev.orientation()).conjugate()).normalize();
        Set<java.util.UUID> ids = SablePhysicalConnectionApi.assemblyBodyIds(level, Set.of(child.getUniqueId()));
        var pipeline = container.physicsSystem().getPipeline();
        for(var id : ids){
            if(!(container.getSubLevel(id) instanceof ServerSubLevel body) || body == parent || body.isRemoved()) continue;
            Vector3d pos = new Vector3d(body.logicalPose().position()).sub(prev.position());
            delta.transform(pos).add(next.position());
            Quaterniond rotation = new Quaterniond(delta).mul(body.logicalPose().orientation()).normalize();
            pipeline.teleport(body, pos, rotation);
            pipeline.resetVelocity(body);
            body.updateLastPose();
            body.updateBoundingBox();
            body.forceUpdateGlobalBounds();
            pipeline.wakeUp(body);
        }
        return true;
    }
}
