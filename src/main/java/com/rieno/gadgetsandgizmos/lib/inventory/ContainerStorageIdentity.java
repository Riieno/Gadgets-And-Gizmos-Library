package com.rieno.gadgetsandgizmos.lib.inventory;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

// Treat each shared chest or Create multiblock inventory as one storage target
public final class ContainerStorageIdentity{
    private ContainerStorageIdentity(){}

    public static BlockPos position(Level level, BlockPos pos){
        if(level == null || !level.isLoaded(pos)) return pos;
        var be = level.getBlockEntity(pos);
        if(be instanceof IMultiBlockEntityContainer multi && multi.getController() != null) return multi.getController().immutable();
        var state = level.getBlockState(pos);
        if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE){
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if(level.isLoaded(other) && level.getBlockState(other).getBlock() == state.getBlock()) return pos.compareTo(other) < 0 ? pos : other;
        }
        return pos;
    }
}
