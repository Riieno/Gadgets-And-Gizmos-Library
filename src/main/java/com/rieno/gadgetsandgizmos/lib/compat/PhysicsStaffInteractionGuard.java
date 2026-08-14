package com.rieno.gadgetsandgizmos.lib.compat;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Authorize Physics Staff targets and protect assemblies during SCM initialization
public final class PhysicsStaffInteractionGuard {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String AEROCLAIMS_MOD_ID = "aeroclaims";
    private static final long MESSAGE_COOLDOWN_MS = 1000L;
    private static final Map<UUID, Long> LAST_MESSAGE_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> INITIALIZING_SUB_LEVELS =
            new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics staff interaction guard
    private PhysicsStaffInteractionGuard() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Authorize a physics staff target
    public static boolean authorizeTarget(ServerPlayer player, UUID targetSubLevelId) {
        if (isInitializationProtected(targetSubLevelId)) {
            showFailureMessage(player,
                    Component.literal("Ship control initialization is in progress"));
            return false;
        }
        if (!PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            return true;
        }

        if (isPlayerOnTargetSubLevel(player, targetSubLevelId)) {
            showFailureMessage(player, "item.createthrusters.physics_staff.error.on_target_sublevel");
            return false;
        }

        if (ModList.get().isLoaded(AEROCLAIMS_MOD_ID)
                && !AeroClaimsAccess.canAccess(player, targetSubLevelId)) {
            showFailureMessage(player, "item.createthrusters.physics_staff.error.claim_denied");
            return false;
        }

        return true;
    }

    // Protect the initialization targets
    public static void protectInitializationTargets(Collection<UUID> subLevelIds) {
        if (subLevelIds == null) {
            return;
        }
        subLevelIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> INITIALIZING_SUB_LEVELS.merge(id, 1, Integer::sum));
    }

    // Release the initialization targets
    public static void releaseInitializationTargets(Collection<UUID> subLevelIds) {
        if (subLevelIds == null) {
            return;
        }
        subLevelIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> INITIALIZING_SUB_LEVELS.computeIfPresent(
                        id, (ignored, count) -> count <= 1 ? null : count - 1));
    }

    // Check if initialization is protected
    public static boolean isInitializationProtected(UUID subLevelId) {
        return subLevelId != null
                && INITIALIZING_SUB_LEVELS.getOrDefault(subLevelId, 0) > 0;
    }

    // Clear the initialization targets
    public static void clearInitializationTargets() {
        INITIALIZING_SUB_LEVELS.clear();
        LAST_MESSAGE_TIME.clear();
    }

    // Check if the player is on the target sublevel
    public static boolean isPlayerOnTargetSubLevel(Player player, UUID targetSubLevelId) {
        if (player == null || targetSubLevelId == null
                || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            return false;
        }

        SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
        return trackingSubLevel != null && targetSubLevelId.equals(trackingSubLevel.getUniqueId());
    }

    // Show the failure message
    private static void showFailureMessage(ServerPlayer player, String translationKey) {
        showFailureMessage(player, Component.translatable(translationKey));
    }

    // Show the failure message
    private static void showFailureMessage(ServerPlayer player, Component msg) {
        long now = System.currentTimeMillis();
        Long lastMessage = LAST_MESSAGE_TIME.get(player.getUUID());
        if (lastMessage != null && now - lastMessage < MESSAGE_COOLDOWN_MS) {
            return;
        }

        LAST_MESSAGE_TIME.put(player.getUUID(), now);
        player.displayClientMessage(msg, true);
    }

    // Expose aero claims
    private static final class AeroClaimsAccess {
        // Initialize the aero claims
        private AeroClaimsAccess() {
        }

        // Check if this can access
        private static boolean canAccess(ServerPlayer player, UUID targetSubLevelId) {
            Claim claim = ClaimManager.getClaimByShipId(player.serverLevel(), targetSubLevelId.toString());
            return claim == null || !claim.isActive() || ClaimManager.getPermissionResolver().canAccess(player, claim);
        }
    }
}
