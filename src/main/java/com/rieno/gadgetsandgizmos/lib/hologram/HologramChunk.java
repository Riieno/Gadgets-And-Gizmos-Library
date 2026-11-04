package com.rieno.gadgetsandgizmos.lib.hologram;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

// Transfer only exposed surface geometry without block entity or inventory data
public record HologramChunk(ChunkPos pos, int grassColor, int foliageColor, int waterColor, List<SurfaceBlock> blocks, Map<BlockPos, Float> kineticSpeeds){
    public static final int MAX_BLOCKS = 65_536;

    // Preserve source compatibility for block-only consumers
    public HologramChunk(ChunkPos pos, int grassColor, int foliageColor, int waterColor, List<SurfaceBlock> blocks){
        this(pos, grassColor, foliageColor, waterColor, blocks, Map.of());
    }

    public HologramChunk{
        kineticSpeeds = Map.copyOf(kineticSpeeds);
        if(kineticSpeeds.size() > 4096) throw new IllegalArgumentException("Too many kinetic snapshots");
        blocks = List.copyOf(blocks);
        if(blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Surface snapshot is too large");
    }

    // Compare the baked surface independently of live kinetic animation snapshots
    public boolean hasSameSurface(HologramChunk other){
        return other != null && (this == other || pos.equals(other.pos)
                && grassColor == other.grassColor && foliageColor == other.foliageColor
                && waterColor == other.waterColor && blocks.equals(other.blocks));
    }

    // Positions are local to the chunk on X/Z and use world height on Y
    public record SurfaceBlock(BlockPos pos, BlockState state, int faces){
        public SurfaceBlock{
            pos = pos.immutable();
            if(pos.getX() < 0 || pos.getX() > 15 || pos.getZ() < 0 || pos.getZ() > 15 || faces < 1 || faces > 63){
                throw new IllegalArgumentException("Invalid surface block");
            }
        }
    }

    // Encode bounded geometry using Minecraft's synchronized block state ids
    public void encode(RegistryFriendlyByteBuf buf){
        buf.writeChunkPos(pos);
        buf.writeInt(grassColor);
        buf.writeInt(foliageColor);
        buf.writeInt(waterColor);
        buf.writeVarInt(blocks.size());
        for(var block : blocks){
            buf.writeByte(block.pos().getX() << 4 | block.pos().getZ());
            buf.writeVarInt(block.pos().getY());
            buf.writeVarInt(Block.getId(block.state()));
            buf.writeByte(block.faces());
        }
        buf.writeVarInt(kineticSpeeds.size());
        kineticSpeeds.forEach((pos, speed) -> { buf.writeBlockPos(pos); buf.writeFloat(speed); });
    }

    // Reject oversized or invalid snapshots before allocating their block lists
    public static HologramChunk decode(RegistryFriendlyByteBuf buf){
        ChunkPos pos = buf.readChunkPos();
        int grass = buf.readInt(), foliage = buf.readInt(), water = buf.readInt();
        int count = buf.readVarInt();
        if(count < 0 || count > MAX_BLOCKS) throw new IllegalArgumentException("Invalid surface block count");
        List<SurfaceBlock> blocks = new ArrayList<>(count);
        for(int idx = 0; idx < count; idx++){
            int xz = buf.readUnsignedByte(), y = buf.readVarInt(), stateId = buf.readVarInt();
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(stateId);
            if(state == null) throw new IllegalArgumentException("Unknown surface block state");
            blocks.add(new SurfaceBlock(new BlockPos(xz >> 4, y, xz & 15), state, buf.readUnsignedByte()));
        }
        int speeds = buf.readVarInt();
        if(speeds < 0 || speeds > 4096) throw new IllegalArgumentException("Invalid kinetic snapshot count");
        Map<BlockPos, Float> kinetics = new LinkedHashMap<>();
        for(int idx = 0; idx < speeds; idx++){
            BlockPos point = buf.readBlockPos();
            float speed = buf.readFloat();
            if(point.getX() < 0 || point.getX() > 15 || point.getZ() < 0 || point.getZ() > 15 || !Float.isFinite(speed)){
                throw new IllegalArgumentException("Invalid kinetic snapshot");
            }
            kinetics.put(point, speed);
        }
        return new HologramChunk(pos, grass, foliage, water, blocks, kinetics);
    }
}
