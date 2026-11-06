package com.rieno.gadgetsandgizmos.lib.entity;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

// Share zipline-style hanging movement without competing with a player's own movement packets
public final class HangingEntityMotion{
    private static final Map<Player, ServerGrip> SERVER_GRIPS = new WeakHashMap<>();
    private HangingEntityMotion(){}

    // Keep the zipline's damping and correction strength in one place
    public static Vec3 velocity(Vec3 current, Vec3 error){
        return current.scale(0.7D).add(error.scale(0.3D));
    }

    // Place the entity's head below the grip instead of pulling its feet into it
    public static Vec3 feetBelowGrip(Vec3 grip, double height, double gap){
        return grip.add(0.0D, -height - gap, 0.0D);
    }

    // Keep a hanging body below its owner's transformed block bounds
    public static Vec3 blockGrip(BlockEntity owner, Vec3 localGrip){
        var body = SableLevelApi.containing(owner);
        return blockGrip(owner.getBlockPos(), body == null ? null : body.logicalPose(), localGrip);
    }

    // Resolve the same bounds from an explicit pose without loading a body
    public static Vec3 blockGrip(BlockPos pos, Pose3dc pose, Vec3 localGrip){
        Vec3 grip = pose == null ? localGrip : pose.transformPosition(localGrip);
        double bottom = Double.POSITIVE_INFINITY;
        for(int x = 0; x <= 1; x++){
            for(int y = 0; y <= 1; y++){
                for(int z = 0; z <= 1; z++){
                    Vec3 corner = new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    bottom = Math.min(bottom, (pose == null ? corner : pose.transformPosition(corner)).y);
                }
            }
        }
        return new Vec3(grip.x, Math.min(grip.y, bottom), grip.z);
    }

    // Resolve feet in world space even while Sable projects an entity into a body
    public static Vec3 worldFeet(Entity entity){
        var feet = Sable.HELPER.getFeetPos(entity, 0.0F);
        return SableTransformApi.toWorldPosition(SableLevelApi.containing(entity), new Vec3(feet.x(), feet.y(), feet.z()));
    }

    // Let the local player predict movement; the server moves other entities
    public static void follow(Entity entity, Vec3 worldGrip, double gap){
        Vec3 target = feetBelowGrip(worldGrip, entity.getBoundingBox().getYsize(), gap);
        Vec3 feet = worldFeet(entity);
        double distance = target.distanceToSqr(feet);
        if(!Double.isFinite(distance) || distance > 64.0D) return;
        entity.fallDistance = 0.0F;
        if(entity instanceof Player player){
            if(!entity.level().isClientSide){
                SERVER_GRIPS.put(player, new ServerGrip(player.tickCount, new WeakReference<>(player.level()), target));
                keepAlive(player);
                if(!(player instanceof FakePlayer)) return;
            }else if(!player.isLocalPlayer()) return;
        }else if(entity.level().isClientSide) return;
        var body = SableLevelApi.containing(entity);
        Vec3 localTarget = SableTransformApi.toLocalPosition(body, target);
        Vec3 localFeet = SableTransformApi.toLocalPosition(body, feet);
        entity.setDeltaMovement(velocity(entity.getDeltaMovement(), localTarget.subtract(localFeet)));
    }

    // Retain Create's hanging pose for the same lifetime as the server-owned grip
    public static void keepAlive(Player player){
        if(player.level().isClientSide) return;
        player.fallDistance = 0.0F;
        ServerChainConveyorHandler.handleTTLPacket(player);
    }

    // Avoid capturing a player who is already hanging from another device
    public static boolean isHanging(Entity entity){
        return entity instanceof Player && !entity.level().isClientSide
                && ServerChainConveyorHandler.hangingPlayers.containsKey(entity.getUUID());
    }

    // Only a nearby grip refreshed by server code can authorize suspended player movement
    public static boolean isServerHeld(Player player){
        if(player.level().isClientSide) return false;
        ServerGrip grip = SERVER_GRIPS.get(player);
        if(grip == null || grip.level().get() != player.level() || player.tickCount < grip.tick()
                || player.tickCount - grip.tick() > 2) return false;
        return grip.target().distanceToSqr(worldFeet(player)) <= 64.0D;
    }

    // Clear the hanging pose immediately when the owner releases its entity
    public static void release(Entity entity){
        if(entity instanceof Player player && !entity.level().isClientSide){
            SERVER_GRIPS.remove(player);
            ServerChainConveyorHandler.handleStopRidingPacket(player);
        }
    }

    private record ServerGrip(int tick, WeakReference<Level> level, Vec3 target){}
}
