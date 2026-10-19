package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.multiplayer.ClientLevel;

// Limit live client particles and new particles in each world tick
public final class ClientParticleBudget {
    private final int maxActive;
    private final int maxSpawnsPerTick;
    private ClientLevel level;
    private long tick = Long.MIN_VALUE;
    private int active;
    private int spawnedThisTick;

    // Set the live and per-tick particle limits
    public ClientParticleBudget(int maxActive, int maxSpawnsPerTick) {
        if (maxActive < 1 || maxSpawnsPerTick < 1) {
            throw new IllegalArgumentException("Particle limits must be positive");
        }
        this.maxActive = maxActive;
        this.maxSpawnsPerTick = maxSpawnsPerTick;
    }

    // Reserve one particle slot in the current client world
    public boolean tryAcquire(ClientLevel level) {
        if (level == null) return false;
        long gameTime = level.getGameTime();
        if (this.level != level || gameTime < this.tick) {
            this.level = level;
            this.tick = gameTime;
            this.active = 0;
            this.spawnedThisTick = 0;
        } else if (gameTime != this.tick) {
            this.tick = gameTime;
            this.spawnedThisTick = 0;
        }
        if (this.active >= this.maxActive || this.spawnedThisTick >= this.maxSpawnsPerTick) return false;
        this.active++;
        this.spawnedThisTick++;
        return true;
    }

    // Return a slot when a particle expires or is removed
    public void release(ClientLevel level) {
        if (this.level == level && this.active > 0) this.active--;
    }
}
