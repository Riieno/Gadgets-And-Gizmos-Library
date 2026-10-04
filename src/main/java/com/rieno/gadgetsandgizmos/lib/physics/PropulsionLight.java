package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;

/** A synced block light level driven by propulsion. */
public final class PropulsionLight {
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light_level", 0, 15);

    private PropulsionLight() {}

    public static int level(double signedThrust, double fullBrightnessThrust) {
        if (!Double.isFinite(signedThrust) || signedThrust == 0.0D
                || !Double.isFinite(fullBrightnessThrust) || fullBrightnessThrust <= 0.0D) return 0;
        double fraction = Math.min(1.0D, Math.abs(signedThrust) / fullBrightnessThrust);
        return Math.max(1, (int) Math.ceil(15.0D * Math.sqrt(fraction)));
    }

    public static int emission(int storedLight, boolean enabled) {
        return enabled ? Mth.clamp(storedLight, 0, 15) : 0;
    }

    public static boolean refreshClient(Level level, BlockPos pos, boolean previousEnabled, boolean enabled) {
        if (level != null && level.isClientSide && previousEnabled != enabled) {
            level.getLightEngine().checkBlock(pos);
        }
        return enabled;
    }

    public static int get(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(LIGHT_LEVEL) ? state.getValue(LIGHT_LEVEL) : 0;
    }

    public static boolean update(Level level, BlockPos pos, int light) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(LIGHT_LEVEL)) return false;
        AuxiliaryLightManager manager = level.getAuxLightManager(pos);
        if (manager != null && manager.getLightAt(pos) != 0) manager.removeLightAt(pos);
        int next = Mth.clamp(light, 0, 15);
        if (state.getValue(LIGHT_LEVEL) == next) return false;
        return level.setBlock(pos, state.setValue(LIGHT_LEVEL, next), Block.UPDATE_CLIENTS);
    }
}
