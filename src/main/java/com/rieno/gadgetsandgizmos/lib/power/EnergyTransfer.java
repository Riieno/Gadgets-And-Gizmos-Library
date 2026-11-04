package com.rieno.gadgetsandgizmos.lib.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.rieno.gadgetsandgizmos.lib.access.LoadedBlockEntities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Transfer FE according to receiver demand and each storage's own transfer limits
public final class EnergyTransfer{
    private EnergyTransfer(){}

    // Supply adjacent consumers without circulating FE between bidirectional storage blocks
    public static long supplyAdjacent(Level level, BlockPos pos, IEnergyStorage source){
        if(level == null || level.isClientSide || source == null || !source.canExtract()) return 0;
        List<IEnergyStorage> targets = new ArrayList<>();
        for(Direction face : Direction.values()){
            var targetPos = pos.relative(face);
            if(!level.isLoaded(targetPos)) continue;
            var state = level.getBlockState(targetPos);
            var entity = LoadedBlockEntities.get(level, targetPos);
            if(state.hasBlockEntity() && entity == null) continue;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, state, entity, face.getOpposite());
            if(target != null && target != source && target.canReceive() && !target.canExtract()) targets.add(target);
        }
        return transfer(List.of(source), targets);
    }

    // Supply machines before charging shared storage, without a network-wide FE budget
    public static long transfer(List<? extends IEnergyStorage> sources, List<? extends IEnergyStorage> targets){
        List<IEnergyStorage> outputs = distinct(sources);
        List<IEnergyStorage> inputs = distinct(targets);
        inputs.sort(Comparator.comparing(IEnergyStorage::canExtract));
        long transferred = 0L;
        for(IEnergyStorage target : inputs){
            if(!target.canReceive()) continue;
            for(IEnergyStorage source : outputs){
                if(source == target || !source.canExtract()) continue;
                int requested = target.receiveEnergy(Integer.MAX_VALUE, true);
                if(requested <= 0) break;
                int offered = source.extractEnergy(requested, true);
                if(offered <= 0) continue;
                int accepted = target.receiveEnergy(offered, true);
                if(accepted <= 0) continue;
                int extracted = source.extractEnergy(accepted, false);
                if(extracted <= 0) continue;
                int delivered = target.receiveEnergy(extracted, false);
                transferred += delivered;
                if(delivered < extracted && source.canReceive()) source.receiveEnergy(extracted - delivered, false);
            }
        }
        return transferred;
    }

    // A multiblock may expose the same storage through several connected faces
    private static List<IEnergyStorage> distinct(List<? extends IEnergyStorage> storages){
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IEnergyStorage> result = new ArrayList<>();
        for(IEnergyStorage storage : storages){
            if(storage != null && seen.add(storage)) result.add(storage);
        }
        return result;
    }
}
