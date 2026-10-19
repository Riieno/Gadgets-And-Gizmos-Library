package com.rieno.gadgetsandgizmos.lib.physics.archive;

import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Build bounded block-only previews without sending inventories or entity data
public final class SubLevelSnapshots{
    private SubLevelSnapshots(){}

    // Count blocks or stop as soon as the server's block limit is exceeded
    public static int blockCount(List<ServerSubLevel> bodies, int limit){
        int count = 0;
        for(ServerSubLevel body : bodies){
            for(var holder : body.getPlot().getLoadedChunks()){
                for(LevelChunkSection section : holder.getChunk().getSections()){
                    if(section.hasOnlyAir()) continue;
                    for(int y = 0; y < 16; y++) for(int z = 0; z < 16; z++) for(int x = 0; x < 16; x++){
                        if(!section.getBlockState(x, y, z).isAir() && ++count > limit) return count;
                    }
                }
            }
        }
        return count;
    }

    // Express every preview block in the root body's frame
    public static ListTag preview(List<ServerSubLevel> bodies, int limit){
        ListTag rows = new ListTag();
        if(bodies.isEmpty()) return rows;
        ServerSubLevel root = bodies.getFirst();
        Vec3 anchor = SableTransformApi.toWorldPosition(root, root.getPlot().getCenterBlock().getCenter());
        int stride = Math.max(1, (blockCount(bodies, 65536) + Math.max(1, limit) - 1) / Math.max(1, limit));
        int seen = 0;
        for(ServerSubLevel body : bodies){
            for(var holder : body.getPlot().getLoadedChunks()){
                LevelChunk chunk = holder.getChunk();
                for(int sectionIdx = 0; sectionIdx < chunk.getSectionsCount(); sectionIdx++){
                    LevelChunkSection section = chunk.getSection(sectionIdx);
                    if(section.hasOnlyAir()) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(sectionIdx) << 4;
                    for(int y = 0; y < 16; y++) for(int z = 0; z < 16; z++) for(int x = 0; x < 16; x++){
                        var state = section.getBlockState(x, y, z);
                        if(state.isAir()) continue;
                        if(seen++ % stride != 0) continue;
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        Vec3 point = SableTransformApi.toWorldPosition(body, pos.getCenter()).subtract(anchor).subtract(0.5, 0.5, 0.5);
                        CompoundTag row = new CompoundTag();
                        row.putUUID("Body", body.getUniqueId());
                        row.putLong("Pos", pos.asLong());
                        row.put("State", NbtUtils.writeBlockState(state));
                        row.putDouble("X", point.x);
                        row.putDouble("Y", point.y);
                        row.putDouble("Z", point.z);
                        var orientation = body.logicalPose().orientation();
                        row.putFloat("Qx", (float) orientation.x());
                        row.putFloat("Qy", (float) orientation.y());
                        row.putFloat("Qz", (float) orientation.z());
                        row.putFloat("Qw", (float) orientation.w());
                        rows.add(row);
                        if(rows.size() >= limit) return rows;
                    }
                }
            }
        }
        return rows;
    }
}
