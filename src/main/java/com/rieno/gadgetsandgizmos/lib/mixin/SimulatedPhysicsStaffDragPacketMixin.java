package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffDragPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Check Physics Staff drag power before Simulated applies the packet
@Mixin(PhysicsStaffDragPacket.class)
public abstract class SimulatedPhysicsStaffDragPacketMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the sublevel
    @Shadow
    public abstract java.util.UUID subLevel();

    // Consume the air for powered drag
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$consumeAirForPoweredDrag(@Coerce Object context, CallbackInfo callbackInfo) {
        ServerPlayer player = extractPlayer(context);
        if (player == null) {
            return;
        }

        if (!PhysicsStaffInteractionGuard.authorizeTarget(player, subLevel())) {
            PhysicsStaffPowerTracker.get(player.server).clearActiveDrag(player.getUUID());
            PhysicsStaffServerHandler.get((ServerLevel) player.level()).stopDragging(player.getUUID());
            callbackInfo.cancel();
            return;
        }

        if (!PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            return;
        }

        if (!PhysicsStaffPowerHooks.authorizeAction(player, true)) {
            PhysicsStaffPowerTracker.get(player.server).clearActiveDrag(player.getUUID());
            PhysicsStaffServerHandler.get((ServerLevel) player.level()).stopDragging(player.getUUID());
            callbackInfo.cancel();
            return;
        }

        java.util.UUID staffId = PhysicsStaffPowerHooks.getCurrentPoweredStaffId(player);
        if (staffId == null) {
            return;
        }

        PhysicsStaffPowerTracker.get(player.server)
                .onDragUpdated((ServerLevel) player.level(), player, staffId, subLevel());
    }

    // Extract the player
    private static ServerPlayer extractPlayer(Object ctx) {
        try {
            return (ServerPlayer) ctx.getClass().getMethod("player").invoke(ctx);
        } catch (ReflectiveOperationException err) {
            return null;
        }
    }
}
