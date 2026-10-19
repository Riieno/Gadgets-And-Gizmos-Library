package com.rieno.gadgetsandgizmos.lib.control;

import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// Read a physical source independently of neighbouring power and controller writes
public final class BlockRedstoneSignals{
    private BlockRedstoneSignals(){}

    // Read an outward face, or the strongest output when no face is selected
    public static int read(Level level, BlockPos pos, @Nullable Direction face){
        if(level == null || pos == null || !level.isLoaded(pos)) return 0;
        BlockState state = level.getBlockState(pos);
        if(level.getBlockEntity(pos) instanceof RedstoneLinkBlockEntity link){
            return Mth.clamp(state.hasProperty(RedstoneLinkBlock.RECEIVER)
                    && state.getValue(RedstoneLinkBlock.RECEIVER)
                    ? link.getReceivedSignal() : link.getSignal(), 0, 15);
        }
        if(face != null) return readFace(level, pos, state, face);
        int signal = 0;
        for(Direction side : Direction.values()) signal = Math.max(signal, readFace(level, pos, state, side));
        return signal;
    }

    // Minecraft queries the neighbour in the direction opposite its outward face
    public static int readFace(Level level, BlockPos pos, BlockState state, Direction face){
        Direction query = face.getOpposite();
        return Mth.clamp(Math.max(state.getSignal(level, pos, query),
                state.getDirectSignal(level, pos, query)), 0, 15);
    }
}
