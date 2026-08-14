package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stop guarded Physics Staff interactions before the client sends them
@Mixin(PhysicsStaffClientHandler.class)
public abstract class SimulatedPhysicsStaffClientHandlerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current drag session
    @Shadow
    private PhysicsStaffClientHandler.ClientDragSession dragSession;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Require the air for punch
    @Inject(method = "onItemPunched", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$requireAirForPunch(CallbackInfo callbackInfo) {
        if (shouldBlockPoweredStaffAction()) {
            callbackInfo.cancel();
        }
    }

    // Require the air for use
    @Inject(method = "onItemUsed", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$requireAirForUse(PhysicsStaffAction action, CallbackInfo callbackInfo) {
        boolean stoppingDrag = action == PhysicsStaffAction.START_DRAG && dragSession != null;
        if (action != PhysicsStaffAction.STOP_DRAG
                && !stoppingDrag
                && (shouldBlockPoweredStaffAction() || gadgetsngizmos$targetsPlayersSubLevel(action))) {
            callbackInfo.cancel();
        }
    }

    // Stop the dragging tracked sublevel
    @Inject(method = "tick", at = @At("HEAD"))
    private void gadgetsngizmos$stopDraggingTrackedSubLevel(CallbackInfo callbackInfo) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || dragSession == null
                || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)
                || !PhysicsStaffInteractionGuard.isPlayerOnTargetSubLevel(
                        player, dragSession.dragSubLevel().getUniqueId())) {
            return;
        }

        ((PhysicsStaffClientHandler) (Object) this).onItemUsed(PhysicsStaffAction.START_DRAG);
    }

    // Check if the action targets the player sublevel
    private boolean gadgetsngizmos$targetsPlayersSubLevel(PhysicsStaffAction action) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)
                || action == PhysicsStaffAction.START_DRAG && dragSession != null) {
            return false;
        }

        SubLevel targetSubLevel;
        if (dragSession != null) {
            targetSubLevel = dragSession.dragSubLevel();
        } else {
            HitResult hit = player.pick(
                    dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem.RANGE, 1.0F, false);
            targetSubLevel = hit instanceof BlockHitResult && hit.getType() != HitResult.Type.MISS
                    ? Sable.HELPER.getContainingClient(hit.getLocation())
                    : null;
        }

        return targetSubLevel != null
                && PhysicsStaffInteractionGuard.isPlayerOnTargetSubLevel(player, targetSubLevel.getUniqueId());
    }

    // Check if this should block powered staff action
    private static boolean shouldBlockPoweredStaffAction() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)
                && !PhysicsStaffPowerHooks.authorizeAction(player, true);
    }
}
