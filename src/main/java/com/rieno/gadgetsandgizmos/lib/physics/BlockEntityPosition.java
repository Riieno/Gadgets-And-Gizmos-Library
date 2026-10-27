package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Resolve ComputerCraft calls against root-world and Sable-local block positions
public final class BlockEntityPosition {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computer craft position
    private BlockEntityPosition(){
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block position
    public static Map<String, Object> blockPosition(BlockEntity blockEntity){
        return blockPosition(blockEntity, blockEntity.getBlockPos());
    }

    // Get the block position
    public static Map<String, Object> blockPosition(BlockEntity coordinateFrame, BlockPos localPos){
        Vec3 localOrigin = Vec3.atLowerCornerOf(localPos);
        Vec3 localCenter = Vec3.atCenterOf(localPos);
        Vec3 worldOrigin = SableTransformApi.projectOut(coordinateFrame.getLevel(),
                SableTransformApi.toWorldPosition(coordinateFrame, localOrigin));
        Vec3 worldCenter = SableTransformApi.projectOut(coordinateFrame.getLevel(),
                SableTransformApi.toWorldPosition(coordinateFrame, localCenter));
        UUID subLevelId = SableLevelApi.containingId(coordinateFrame);
        return positionMap(coordinateFrame.getLevel(), worldOrigin, worldCenter, localPos, subLevelId);
    }

    // Get the block position
    public static Map<String, Object> blockPosition(Level level, @Nullable UUID subLevelId, BlockPos localPos){
        if(subLevelId == null){
            return positionMap(level, Vec3.atLowerCornerOf(localPos), Vec3.atCenterOf(localPos), localPos, null);
        }

        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, subLevelId);
        if(subLevel == null){
            return unresolvedPositionMap(level, localPos, subLevelId);
        }

        Vec3 worldOrigin = SableTransformApi.toWorldPosition((dev.ryanhcode.sable.sublevel.SubLevel)subLevel, Vec3.atLowerCornerOf(localPos));
        Vec3 worldCenter = SableTransformApi.toWorldPosition((dev.ryanhcode.sable.sublevel.SubLevel)subLevel, Vec3.atCenterOf(localPos));
        worldOrigin = SableTransformApi.projectOut(level, worldOrigin);
        worldCenter = SableTransformApi.projectOut(level, worldCenter);
        return positionMap(level, worldOrigin, worldCenter, localPos, subLevelId);
    }

    // Get the world position
    public static Map<String, Object> worldPosition(Level level, Vec3 pos){
        Map<String, Object> out = new LinkedHashMap<>();
        putWorldCoordinates(out, pos);
        out.put("space", "world");
        out.put("dimension", dimension(level));
        out.put("projected", true);
        return out;
    }

    // Get the world direction
    public static Map<String, Object> worldDirection(BlockEntity blockEntity, Direction localDirection){
        Vec3 localOrigin = Vec3.atCenterOf(blockEntity.getBlockPos());
        Vec3 localEnd = localOrigin.add(Vec3.atLowerCornerOf(localDirection.getNormal()));
        Vec3 worldOrigin = SableTransformApi.projectOut(blockEntity.getLevel(),
                SableTransformApi.toWorldPosition(blockEntity, localOrigin));
        Vec3 worldEnd = SableTransformApi.projectOut(blockEntity.getLevel(),
                SableTransformApi.toWorldPosition(blockEntity, localEnd));
        Vec3 worldDirection = worldEnd.subtract(worldOrigin);
        if(worldDirection.lengthSqr() > 1.0E-12D){
            worldDirection = worldDirection.normalize();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", worldDirection.x);
        out.put("y", worldDirection.y);
        out.put("z", worldDirection.z);
        out.put("space", "world");
        return out;
    }

    // Get the position map
    private static Map<String, Object> positionMap(Level level, Vec3 worldOrigin, Vec3 worldCenter,
                                                   BlockPos localPos, @Nullable UUID subLevelId){
        Map<String, Object> out = new LinkedHashMap<>();
        putWorldCoordinates(out, worldOrigin);
        out.put("centerX", worldCenter.x);
        out.put("centerY", worldCenter.y);
        out.put("centerZ", worldCenter.z);
        out.put("localX", localPos.getX());
        out.put("localY", localPos.getY());
        out.put("localZ", localPos.getZ());
        out.put("subLevelId", subLevelId == null ? "" : subLevelId.toString());
        out.put("dimension", dimension(level));
        out.put("space", "world");
        out.put("projected", true);
        return out;
    }

    // Get the unresolved position map
    private static Map<String, Object> unresolvedPositionMap(Level level, BlockPos localPos, UUID subLevelId){
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("localX", localPos.getX());
        out.put("localY", localPos.getY());
        out.put("localZ", localPos.getZ());
        out.put("subLevelId", subLevelId.toString());
        out.put("dimension", dimension(level));
        out.put("space", "sublevel");
        out.put("projected", false);
        return out;
    }

    // Put the world coordinates
    private static void putWorldCoordinates(Map<String, Object> out, Vec3 pos){
        out.put("x", pos.x);
        out.put("y", pos.y);
        out.put("z", pos.z);
        BlockPos blockPos = BlockPos.containing(pos);
        out.put("blockX", blockPos.getX());
        out.put("blockY", blockPos.getY());
        out.put("blockZ", blockPos.getZ());
    }

    // Get the dimension
    private static String dimension(@Nullable Level level){
        return level == null ? "" : level.dimension().location().toString();
    }
}
