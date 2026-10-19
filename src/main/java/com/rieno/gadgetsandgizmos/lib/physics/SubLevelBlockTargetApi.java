package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Keep a block's stable binding and selected face across native assembly transfers
public final class SubLevelBlockTargetApi{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Direction[] AXES = {Direction.EAST, Direction.UP, Direction.SOUTH};
    private static final double MARKER_OFFSET = 0.25D;

    private SubLevelBlockTargetApi(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bind a loaded block once and follow its native tracking point without changing its target ID
    public static @Nullable ControllerDirectTargetReference resolve(@Nullable Level level,
            @Nullable ControllerDirectTargetReference target){
        if(level == null || target == null || !target.isBound() || target.blockPos() == null) return null;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if(serverLevel == null || serverLevel.getServer() == null) return target;
        var points = SubLevelTrackingPointSavedData.getOrLoad(serverLevel);
        UUID centerId = pointId(serverLevel, target.targetId(), "center");
        TrackingPoint center = points.getTrackingPoint(centerId);
        if(center == null){
            if(!SubLevelBlockEntityCollector.isTargetLoaded(serverLevel, target.subLevelId(), target.blockPos())
                    || serverLevel.getBlockState(target.blockPos()).isAir()) return null;
            Vector3d pos = new Vector3d(target.blockPos().getX() + 0.5D,
                    target.blockPos().getY() + 0.5D, target.blockPos().getZ() + 0.5D);
            center = point(target.subLevelId(), pos);
            points.setTrackingPoint(centerId, center);
            // Keep every marker inside the block so native bounds transfer all of them together
            for(Direction axis : AXES){
                points.setTrackingPoint(pointId(serverLevel, target.targetId(), axis.getSerializedName()),
                        point(target.subLevelId(), new Vector3d(pos).add(axis.getStepX() * MARKER_OFFSET,
                                axis.getStepY() * MARKER_OFFSET, axis.getStepZ() * MARKER_OFFSET)));
            }
        }
        BlockPos pos = BlockPos.containing(center.point().x(), center.point().y(), center.point().z());
        UUID subLevelId = center.inSubLevel() ? center.subLevelID() : SableLevelApi.containingId(serverLevel, pos);
        if(!SubLevelBlockEntityCollector.isTargetLoaded(serverLevel, subLevelId, pos)
                || serverLevel.getBlockState(pos).isAir()) return null;
        return target.withLocation(subLevelId, pos);
    }

    // Rotate the original selected face with its block while retaining the saved face selection
    public static @Nullable Direction resolveFace(@Nullable Level level,
            @Nullable ControllerDirectTargetReference target){
        if(target == null || target.face() == null) return null;
        ControllerDirectTargetReference resolved = resolve(level, target);
        if(resolved == null) return null;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if(serverLevel == null || serverLevel.getServer() == null) return target.face();
        var points = SubLevelTrackingPointSavedData.getOrLoad(serverLevel);
        TrackingPoint center = points.getTrackingPoint(pointId(serverLevel, target.targetId(), "center"));
        Vector3d dir = new Vector3d();
        int[] steps = {target.face().getStepX(), target.face().getStepY(), target.face().getStepZ()};
        for(int idx = 0; idx < AXES.length; idx++){
            if(steps[idx] == 0) continue;
            TrackingPoint marker = points.getTrackingPoint(pointId(serverLevel, target.targetId(), AXES[idx].getSerializedName()));
            if(marker == null) return null;
            dir.fma(steps[idx], new Vector3d(marker.point()).sub(center.point()));
        }
        if(!dir.isFinite() || dir.lengthSquared() < 1.0E-10D) return null;
        return Direction.getNearest((float)dir.x, (float)dir.y, (float)dir.z);
    }

    // Derive shared marker IDs from the caller's stable binding and owning dimension
    private static UUID pointId(ServerLevel level, String targetId, String marker){
        String key = "gadgetsngizmos:block_target:" + level.dimension().location() + ":" + targetId + ":" + marker;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    // Store one native point in the same coordinate frame as its target block
    private static TrackingPoint point(@Nullable UUID subLevelId, Vector3d pos){
        return new TrackingPoint(subLevelId != null, subLevelId, null, pos, null);
    }
}
