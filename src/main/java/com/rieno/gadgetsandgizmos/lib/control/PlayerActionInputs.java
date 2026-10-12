package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

// Retain short-lived, server-validated player actions without saving their key assignments
public final class PlayerActionInputs {
    private static final Map<Player, Sample> INPUTS = new WeakHashMap<>();

    private PlayerActionInputs() {}

    public static void update(Player player, int held) {
        if (player.level().isClientSide) throw new IllegalArgumentException("Player actions belong to the server");
        if (held == 0 || !player.isAlive() || player.isSpectator()) INPUTS.remove(player);
        else INPUTS.put(player, new Sample(held, player.tickCount));
    }

    public static boolean isHeld(Player player, int action) {
        Sample sample = INPUTS.get(player);
        if (sample == null) return false;
        if (player.tickCount - sample.tick() > 40 || player.tickCount < sample.tick()) {
            INPUTS.remove(player);
            return false;
        }
        return (sample.held() & action) != 0;
    }

    private record Sample(int held, int tick) {}
}
