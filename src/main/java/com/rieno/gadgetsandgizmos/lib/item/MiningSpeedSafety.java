package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

// Recover a usable mining speed when modded attributes or excessive enchantment levels overflow
public final class MiningSpeedSafety {
    private static final double MAX_EFFICIENCY_BONUS = 1024.0D;

    private MiningSpeedSafety() {
    }

    // Recover an invalid pickaxe mining speed while leaving all valid calculations untouched
    public static float recoverInvalidPickaxeSpeed(Player player, BlockState state, float speed) {
        if (Float.isFinite(speed) && speed > 0.0F) {
            return speed;
        }
        if (player == null || state == null) {
            return speed;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
            return speed;
        }
        float baseSpeed = stack.getDestroySpeed(state);
        if (!Float.isFinite(baseSpeed) || baseSpeed <= 0.0F) {
            return speed;
        }

        int efficiencyLevel = 0;
        try {
            Holder<Enchantment> efficiency = player.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.EFFICIENCY);
            efficiencyLevel = Math.max(0, stack.getEnchantmentLevel(efficiency));
        } catch (RuntimeException ignored) {
        }
        double enchantmentBonus = efficiencyLevel <= 0 || baseSpeed <= 1.0F
                ? 0.0D
                : Math.min(MAX_EFFICIENCY_BONUS,
                (double) efficiencyLevel * (double) efficiencyLevel + 1.0D);
        return (float) Math.max(1.0D, baseSpeed + enchantmentBonus);
    }
}
