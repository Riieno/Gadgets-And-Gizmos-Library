package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// Publish a controlled state and rebuild its kinetic network on the owning game thread
public final class KineticBlockStateControl{
    private KineticBlockStateControl(){}

    public static boolean apply(KineticBlockEntity target, BlockState state){
        if(target == null || state == null || target.isRemoved()) return false;
        Level level = target.getLevel();
        if(level == null || level.isClientSide || state.getBlock() != target.getBlockState().getBlock()
                || state.equals(target.getBlockState())) return false;
        target.detachKinetics();
        boolean changed = level.setBlock(target.getBlockPos(), state, 2);
        if(!target.isRemoved()){
            target.attachKinetics();
            if(changed){
                target.setChanged();
                target.sendData();
            }
        }
        return changed;
    }
}
