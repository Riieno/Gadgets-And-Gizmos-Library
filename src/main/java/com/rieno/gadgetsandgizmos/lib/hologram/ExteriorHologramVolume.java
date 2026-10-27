package com.rieno.gadgetsandgizmos.lib.hologram;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

// Flood exterior air through a loaded chunk and its border without treating an overhead platform as solid ground
final class ExteriorHologramVolume implements BlockGetter{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 18;
    private static final int AREA = WIDTH * WIDTH;
    private final int x;
    private final int z;
    private final int minY;
    private final int height;
    private final int[] tops = new int[AREA];
    private final BlockState[] states;
    private final boolean[] exterior;
    private final byte[] closedFaces;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    ExteriorHologramVolume(ServerLevel level, LevelChunk chunk){
        x = chunk.getPos().getMinBlockX() - 1;
        z = chunk.getPos().getMinBlockZ() - 1;
        minY = level.getMinBuildHeight();
        LevelChunk[] neighbors = new LevelChunk[9];
        for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++){
            neighbors[(dz + 1) * 3 + dx + 1] = dx == 0 && dz == 0 ? chunk
                    : level.getChunkSource().getChunkNow(chunk.getPos().x + dx, chunk.getPos().z + dz);
        }
        int maxY = minY;
        for(int bz = 0; bz < WIDTH; bz++) for(int bx = 0; bx < WIDTH; bx++){
            LevelChunk neighbor = neighbors[neighborIndex(bx, bz)];
            int top = neighbor == null ? minY - 1 : SurfaceHologramSampler.surfaceHeight(neighbor, (x + bx) & 15, (z + bz) & 15, minY);
            tops[bz * WIDTH + bx] = top;
            maxY = Math.max(maxY, top + 2);
        }
        height = Math.max(1, Math.min(level.getMaxBuildHeight(), maxY) - minY);
        states = new BlockState[AREA * height];
        exterior = new boolean[states.length];
        closedFaces = new byte[states.length];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for(int bz = 0; bz < WIDTH; bz++) for(int bx = 0; bx < WIDTH; bx++){
            LevelChunk neighbor = neighbors[neighborIndex(bx, bz)];
            for(int by = 0; by < height; by++){
                states[by * AREA + bz * WIDTH + bx] = neighbor == null ? Blocks.AIR.defaultBlockState()
                        : neighbor.getBlockState(cursor.set(x + bx, minY + by, z + bz));
            }
        }
        flood();
    }

    private static int neighborIndex(int x, int z){
        return (z == 0 ? 0 : z == WIDTH - 1 ? 2 : 1) * 3 + (x == 0 ? 0 : x == WIDTH - 1 ? 2 : 1);
    }

    int surfaceHeight(int x, int z){ return tops[(z + 1) * WIDTH + x + 1]; }
    int maxY(){ return minY + height; }

    // Air at the border remains potentially visible even beneath a roof spanning the whole chunk
    private void flood(){
        int[] queue = new int[states.length];
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for(int by = 0; by < height; by++) for(int bz = 0; bz < WIDTH; bz++) for(int bx = 0; bx < WIDTH; bx++){
            int idx = by * AREA + bz * WIDTH + bx;
            BlockState state = states[idx];
            cursor.set(x + bx, minY + by, z + bz);
            boolean fluid = state.getRenderShape() == RenderShape.INVISIBLE && state.getFluidState().isSource();
            int faces = 0;
            if(fluid || !state.is(BlockTags.LEAVES) && state.isSolidRender(this, cursor)) faces = 63;
            else if(state.canOcclude() && !state.is(BlockTags.LEAVES)){
                for(Direction dir : Direction.values()){
                    if(Block.isShapeFullBlock(state.getFaceOcclusionShape(this, cursor, dir))) faces |= 1 << dir.ordinal();
                }
            }
            closedFaces[idx] = (byte)faces;
            if(by == height - 1 && open(faces, Direction.UP)
                    || bx == 0 && open(faces, Direction.WEST) || bx == WIDTH - 1 && open(faces, Direction.EAST)
                    || bz == 0 && open(faces, Direction.NORTH) || bz == WIDTH - 1 && open(faces, Direction.SOUTH)){
                exterior[idx] = true;
                queue[count++] = idx;
            }
        }
        for(int head = 0; head < count; head++){
            int idx = queue[head], bx = idx % WIDTH, bz = idx / WIDTH % WIDTH;
            if(bx > 0) count = visit(idx, idx - 1, Direction.WEST, queue, count);
            if(bx < WIDTH - 1) count = visit(idx, idx + 1, Direction.EAST, queue, count);
            if(bz > 0) count = visit(idx, idx - WIDTH, Direction.NORTH, queue, count);
            if(bz < WIDTH - 1) count = visit(idx, idx + WIDTH, Direction.SOUTH, queue, count);
            if(idx >= AREA) count = visit(idx, idx - AREA, Direction.DOWN, queue, count);
            if(idx + AREA < states.length) count = visit(idx, idx + AREA, Direction.UP, queue, count);
        }
    }

    private static boolean open(int faces, Direction dir){ return (faces & 1 << dir.ordinal()) == 0; }

    private int visit(int from, int idx, Direction dir, int[] queue, int count){
        if(!exterior[idx] && open(closedFaces[from], dir) && open(closedFaces[idx], dir.getOpposite())){
            exterior[idx] = true;
            queue[count++] = idx;
        }
        return count;
    }

    // Skip buried solids before allocating neighbor positions for their individual face tests
    boolean exposed(BlockPos pos){
        int idx = index(pos);
        return idx >= 0 && (exterior[idx] || idx + AREA >= states.length || exterior[idx - 1] || exterior[idx + 1]
                || exterior[idx - WIDTH] || exterior[idx + WIDTH] || idx >= AREA && exterior[idx - AREA]
                || idx + AREA < states.length && exterior[idx + AREA]);
    }

    boolean exposesFace(BlockPos pos, Direction dir){
        if(exterior(pos.relative(dir))) return true;
        int idx = index(pos);
        return idx >= 0 && exterior[idx] && open(closedFaces[idx], dir);
    }

    boolean exterior(BlockPos pos){
        if(pos.getY() >= maxY()) return true;
        int idx = index(pos);
        return idx >= 0 && exterior[idx];
    }

    private int index(BlockPos pos){
        int bx = pos.getX() - x, bz = pos.getZ() - z, by = pos.getY() - minY;
        if(bx < 0 || bx >= WIDTH || bz < 0 || bz >= WIDTH || by < 0 || by >= height) return -1;
        return by * AREA + bz * WIDTH + bx;
    }

    @Override public BlockState getBlockState(BlockPos pos){
        int idx = index(pos);
        return idx < 0 ? Blocks.AIR.defaultBlockState() : states[idx];
    }
    @Override public FluidState getFluidState(BlockPos pos){ return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos){ return null; }
    @Override public int getMinBuildHeight(){ return minY; }
    @Override public int getHeight(){ return height; }
}
