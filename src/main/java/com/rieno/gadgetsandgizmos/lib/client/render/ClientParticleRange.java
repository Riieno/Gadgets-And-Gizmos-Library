package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

// Spawn client particles within the effective view distance
public final class ClientParticleRange{
    // Prevent construction of the particle range helper
    private ClientParticleRange(){
    }

    // Use the view distance while keeping the client's particle quality setting
    public static void addWithinViewDistance(ClientLevel level, ParticleOptions options, Vec3 pos, Vec3 velocity){
        if(level == null || options == null || pos == null || velocity == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level != level || !level.hasChunkAt(BlockPos.containing(pos))) return;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double range = Math.max(2, minecraft.options.getEffectiveRenderDistance()) * 16.0D;
        if(Math.max(Math.abs(pos.x - camera.x), Math.abs(pos.z - camera.z)) > range) return;
        if(camera.distanceToSqr(pos) <= 1024.0D){
            level.addParticle(options, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
            return;
        }
        if(!options.getType().getOverrideLimiter()){
            switch(minecraft.options.particles().get()){
                case MINIMAL -> { return; }
                case DECREASED -> {
                    if(level.random.nextInt(3) == 0) return;
                }
                default -> { }
            }
        }
        level.addParticle(options, true, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
    }
}
