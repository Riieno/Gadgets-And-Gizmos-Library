package com.rieno.gadgetsandgizmos.lib.physics.archive;

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Validate the complete destination bounds before any archived plot is allocated
final class SubLevelArchivePlacement{
    private SubLevelArchivePlacement(){}

    static void validate(ServerPlayer player, ServerLevel level, SubLevelData data, Vec3 offset){
        var bounds = data.bounds();
        validate(player, level, new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()).move(offset));
    }

    static void validate(ServerPlayer player, ServerLevel level, AABB box){
        Validation validation = new Validation(player, level, box);
        while(!validation.advance(() -> true)){}
    }

    // Check destination cells incrementally before allocating any native plots
    static final class Validation{
        private final ServerPlayer player;
        private final ServerLevel level;
        private final AABB box;
        private final java.util.Iterator<BlockPos> cells;
        private boolean complete;

        Validation(ServerPlayer player, ServerLevel level, AABB box){
            this.player = player;
            this.level = level;
            this.box = box;
            if(!Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)
                    || box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                    || box.getXsize() > 512 || box.getYsize() > 512 || box.getZsize() > 512)
                throw new IllegalArgumentException("Assembly does not fit within the extraction bounds");
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            if(!level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
                throw new IllegalArgumentException("Assembly crosses the world border");
            long volume = (long)(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            if(volume > 1_000_000) throw new IllegalArgumentException("Assembly extraction area exceeds the safety limit");
            cells = BlockPos.betweenClosed(min, max).iterator();
        }

        boolean advance(java.util.function.BooleanSupplier available){
            if(complete) return true;
            int checked = 0;
            while(cells.hasNext() && checked++ < 4096 && available.getAsBoolean()){
                BlockPos pos = cells.next();
                if(!level.hasChunkAt(pos)) throw new IllegalArgumentException("Load the complete extraction area first");
                if(!WorldAccessPolicy.canAccess(player, level, null, pos))
                    throw new IllegalArgumentException("Assembly extraction area is protected");
                var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if(!shape.isEmpty() && net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                        net.minecraft.world.phys.shapes.Shapes.create(box), shape.move(pos.getX(), pos.getY(), pos.getZ()),
                        net.minecraft.world.phys.shapes.BooleanOp.AND))
                    throw new IllegalArgumentException("Clear the assembly's extraction area first");
            }
            if(cells.hasNext()) return false;
            if(!level.getEntityCollisions(null, box).isEmpty())
                throw new IllegalArgumentException("Clear the assembly's extraction area first");
            for(var body : SubLevelContainer.getContainer(level).getAllSubLevels()){
                var other = body.boundingBox();
                if(box.intersects(other.minX(), other.minY(), other.minZ(), other.maxX(), other.maxY(), other.maxZ()))
                    throw new IllegalArgumentException("Another sublevel occupies the extraction area");
            }
            complete = true;
            return true;
        }
    }
}
