package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffBeamPacket;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Check protected targets and power before Simulated Physics Staff actions run
@Mixin(PhysicsStaffActionPacket.class)
public abstract class SimulatedPhysicsStaffActionPacketMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current action
    @Shadow
    protected PhysicsStaffAction action;

    // Current sub-level
    @Shadow
    protected java.util.UUID subLevel;

    // Current location
    @Shadow
    protected Vector3d location;

    // Tracks whether this was locked before toggle
    @Unique
    private boolean gadgetsngizmos$wasLockedBeforeToggle;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Consume the air for powered lock
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$consumeAirForPoweredLock(@Coerce Object context, CallbackInfo callbackInfo) {
        Player player = extractPlayer(context);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        boolean holdingPoweredStaff = PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(serverPlayer);
        if (action == PhysicsStaffAction.STOP_DRAG) {
            PhysicsStaffPowerTracker.get(serverPlayer.server)
                    .clearActiveDrag(serverPlayer.getUUID());
        }

        if (!PhysicsStaffInteractionGuard.authorizeTarget(serverPlayer, subLevel)) {
            PhysicsStaffPowerTracker.get(serverPlayer.server).clearActiveDrag(serverPlayer.getUUID());
            PhysicsStaffServerHandler.get((ServerLevel) serverPlayer.level())
                    .stopDragging(serverPlayer.getUUID());
            callbackInfo.cancel();
            return;
        }

        if (!holdingPoweredStaff) {
            return;
        }

        ServerLevel level = (ServerLevel) serverPlayer.level();
        if (action == PhysicsStaffAction.LOCK) {
            gadgetsngizmos$wasLockedBeforeToggle = PhysicsStaffPowerTracker.isLocked(level, subLevel);
        }

        if (!PhysicsStaffPowerHooks.authorizeAction(serverPlayer, true)) {
            callbackInfo.cancel();
            return;
        }

        if (gadgetsngizmos$holdsVanillaPhysicsStaff(serverPlayer)) {
            return;
        }

        gadgetsngizmos$handlePoweredStaff(level, serverPlayer);
        if (action == PhysicsStaffAction.LOCK) {
            java.util.UUID staffId = PhysicsStaffPowerHooks.getCurrentPoweredStaffId(serverPlayer);
            if (staffId != null) {
                PhysicsStaffPowerTracker.get(serverPlayer.server)
                        .onLockToggled(level, serverPlayer, staffId, subLevel, gadgetsngizmos$wasLockedBeforeToggle);
            }
        }
        callbackInfo.cancel();
    }

    // Track the powered staff locks
    @Inject(method = "handle", at = @At("TAIL"))
    private void gadgetsngizmos$trackPoweredStaffLocks(@Coerce Object context, CallbackInfo callbackInfo) {
        if (action != PhysicsStaffAction.LOCK) {
            return;
        }

        Player player = extractPlayer(context);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        java.util.UUID staffId = PhysicsStaffPowerHooks.getCurrentPoweredStaffId(serverPlayer);
        if (staffId == null) {
            return;
        }

        PhysicsStaffPowerTracker.get(serverPlayer.server)
                .onLockToggled((ServerLevel) serverPlayer.level(), serverPlayer, staffId, subLevel, gadgetsngizmos$wasLockedBeforeToggle);
    }

    // Extract the player
    private static Player extractPlayer(Object ctx) {
        try {
            return (Player) ctx.getClass().getMethod("player").invoke(ctx);
        } catch (ReflectiveOperationException err) {
            return null;
        }
    }

    // Handle the powered staff
    @Unique
    private void gadgetsngizmos$handlePoweredStaff(ServerLevel level, ServerPlayer player) {
        if (action == PhysicsStaffAction.LOCK) {
            PhysicsStaffServerHandler.get(level).toggleLock(subLevel);
            gadgetsngizmos$sendLockBeam(level, player);
        }

        if (action == PhysicsStaffAction.STOP_DRAG) {
            PhysicsStaffServerHandler.get(level).stopDragging(player.getUUID());
        }
    }

    // Send the lock beam
    @Unique
    private void gadgetsngizmos$sendLockBeam(ServerLevel level, ServerPlayer player) {
        Vector3d beamStart = JOMLConversion.toJOML((Position) player.getEyePosition());
        Vector3d beamEnd = new Vector3d(location);
        ChunkPos chunk = new ChunkPos(BlockPos.containing(location.x(), location.y(), location.z()));
        ClientboundCustomPayloadPacket beamPacket =
                new ClientboundCustomPayloadPacket(new PhysicsStaffBeamPacket(player.getUUID(), beamStart, beamEnd));
        for (ServerPlayer otherPlayer : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            if (otherPlayer == player) {
                continue;
            }
            otherPlayer.connection.send((Packet<?>) beamPacket);
        }
    }

    // Check if the player holds a vanilla physics staff
    @Unique
    private static boolean gadgetsngizmos$holdsVanillaPhysicsStaff(Player player) {
        return gadgetsngizmos$isVanillaPhysicsStaff(player.getMainHandItem())
                || gadgetsngizmos$isVanillaPhysicsStaff(player.getOffhandItem());
    }

    // Check if this is a vanilla physics staff
    @Unique
    private static boolean gadgetsngizmos$isVanillaPhysicsStaff(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof PhysicsStaffItem
                && !PhysicsStaffPowerHooks.isPoweredPhysicsStaff(stack);
    }
}
