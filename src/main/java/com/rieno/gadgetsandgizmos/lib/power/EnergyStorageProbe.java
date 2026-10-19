package com.rieno.gadgetsandgizmos.lib.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

// Read exact FE totals when available and preserve standard capability support
public final class EnergyStorageProbe{
    public record Reading(long stored, long capacity){}

    private EnergyStorageProbe(){}

    // Find exact storage without loading an unavailable chunk
    public static @Nullable LongEnergyStorage findExact(@Nullable Level level, @Nullable BlockPos pos){
        if(level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockEntity target = level.getBlockEntity(pos);
        return target instanceof LongEnergyStorage storage ? storage : null;
    }

    // Prefer exact totals over an integer capability's capped values
    public static Reading read(@Nullable IEnergyStorage standard, @Nullable LongEnergyStorage exact){
        if(exact != null) return new Reading(exact.getEnergyStored(), exact.getMaxEnergyStored());
        if(standard != null) return new Reading(standard.getEnergyStored(), standard.getMaxEnergyStored());
        return new Reading(0L, 0L);
    }
}
