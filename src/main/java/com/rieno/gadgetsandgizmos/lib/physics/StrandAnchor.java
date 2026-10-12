package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Retain a strand position and its saved owner without depending on a rope implementation
public record StrandAnchor(UUID strandId, float pathPosition, @Nullable BlockPos ownerPos,
                           @Nullable UUID ownerSubLevelId){
    // Validate the path position and retain an immutable owner position
    public StrandAnchor{
        Objects.requireNonNull(strandId, "strandId");
        if(!Float.isFinite(pathPosition) || pathPosition < 0.0f){
            throw new IllegalArgumentException("Path position must be finite and nonnegative");
        }
        ownerPos = ownerPos == null ? null : ownerPos.immutable();
        ownerSubLevelId = ownerPos == null ? null : ownerSubLevelId;
    }

    // Add owner information when a legacy strand becomes available
    public StrandAnchor withOwner(BlockPos pos, @Nullable UUID subLevelId){
        return new StrandAnchor(strandId, pathPosition, pos, subLevelId);
    }

    // Save the anchor using the existing knot keys and optional owner keys
    public void write(CompoundTag tag){
        tag.putUUID("AnchorRopeUUID", strandId);
        tag.putFloat("AnchorPathPosition", pathPosition);
        if(ownerPos != null){
            tag.putLong("AnchorOwnerPos", ownerPos.asLong());
        }else{
            tag.remove("AnchorOwnerPos");
        }
        if(ownerSubLevelId != null){
            tag.putUUID("AnchorOwnerSubLevelUUID", ownerSubLevelId);
        }else{
            tag.remove("AnchorOwnerSubLevelUUID");
        }
    }

    // Read saved and legacy anchors without inventing an unavailable owner
    public static @Nullable StrandAnchor read(CompoundTag tag){
        if(!tag.hasUUID("AnchorRopeUUID")) return null;
        float pos = tag.getFloat("AnchorPathPosition");
        if(!Float.isFinite(pos) || pos < 0.0f) return null;
        BlockPos owner = tag.contains("AnchorOwnerPos") ? BlockPos.of(tag.getLong("AnchorOwnerPos")) : null;
        UUID subLevelId = tag.hasUUID("AnchorOwnerSubLevelUUID") ? tag.getUUID("AnchorOwnerSubLevelUUID") : null;
        return new StrandAnchor(tag.getUUID("AnchorRopeUUID"), pos, owner, subLevelId);
    }

    // Classify a missing strand only after its saved owner's data is available
    public boolean isBroken(boolean ownerLoaded, @Nullable UUID ownedStrandId){
        return ownerPos != null && ownerLoaded && !strandId.equals(ownedStrandId);
    }

    // Sample this anchor from the current strand points
    public @Nullable Vec3 sample(List<? extends Vector3dc> points){
        return samplePath(points, pathPosition);
    }

    // Sample an arc distance, skipping collapsed segments and clamping to the ends
    public static @Nullable Vec3 samplePath(List<? extends Vector3dc> points, float pos){
        if(points.isEmpty() || !Float.isFinite(pos)) return null;
        double remaining = Math.max(0.0D, pos);
        Vector3dc prev = points.getFirst();
        if(!isFinite(prev)) return null;
        for(int idx = 1; idx < points.size(); idx++){
            Vector3dc next = points.get(idx);
            if(!isFinite(next)) return null;
            double length = prev.distance(next);
            if(length > 1.0E-8D){
                if(remaining <= length){
                    double fraction = remaining / length;
                    return new Vec3(prev.x() + (next.x() - prev.x()) * fraction,
                            prev.y() + (next.y() - prev.y()) * fraction,
                            prev.z() + (next.z() - prev.z()) * fraction);
                }
                remaining -= length;
            }
            prev = next;
        }
        return new Vec3(prev.x(), prev.y(), prev.z());
    }

    // Reject points that cannot be used as a physical attachment
    private static boolean isFinite(Vector3dc point){
        return Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }
}
