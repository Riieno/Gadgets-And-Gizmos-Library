package com.rieno.gadgetsandgizmos.lib.compat;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyCache;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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

    private static final long MESSAGE_COOLDOWN_MS = 1000L;
    private static final Map<UUID, Long> LAST_MESSAGE_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> INITIALIZING_SUB_LEVELS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, SableAssemblyTopologyCache> MOVEMENT_TOPOLOGIES =
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
        if (player != null && player.server != null && targetSubLevelId != null
                && !ShipPermissionManager.get(player.server).allows(
                targetSubLevelId, player.getUUID(), ShipPermission.PHYSICS_STAFF)) {
            showFailureMessage(player, Component.literal("Ship permission denied"));
            return false;
        }
        if(targetSubLevelId != null){
            SubLevel body = SableLevelApi.subLevel(player.serverLevel(), targetSubLevelId);
            if(body == null) return false;
            var point = body.logicalPose().position();
            if(!WorldAccessPolicy.canAccess(player, player.serverLevel(), targetSubLevelId,
                    BlockPos.containing(point.x, point.y, point.z))){
                showFailureMessage(player, "item.createthrusters.physics_staff.error.claim_denied");
                return false;
            }
        }

        return true;
    }

    // Authorize a physics staff movement target
    public static boolean authorizeMovementTarget(ServerPlayer player, UUID targetSubLevelId) {
        if (!authorizeTarget(player, targetSubLevelId)) {
            return false;
        }
        if (isPlayerOnConnectedTargetSubLevel(player, targetSubLevelId)) {
            showFailureMessage(player, "item.createthrusters.physics_staff.error.on_target_sublevel");
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
        MOVEMENT_TOPOLOGIES.clear();
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

    // Check if the target is the player's sublevel or a connected nested sublevel
    public static boolean isPlayerOnConnectedTargetSubLevel(ServerPlayer player, UUID targetSubLevelId) {
        if (player == null || targetSubLevelId == null
                || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            return false;
        }

        SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
        if (trackingSubLevel == null) {
            return false;
        }
        if (targetSubLevelId.equals(trackingSubLevel.getUniqueId())) {
            return true;
        }
        if (!(trackingSubLevel instanceof ServerSubLevel trackedServerSubLevel)) {
            return false;
        }

        SableAssemblyTopologyCache topology = MOVEMENT_TOPOLOGIES.computeIfAbsent(
                player.getUUID(), ignored -> new SableAssemblyTopologyCache());
        return topology.get(trackedServerSubLevel).loadedBodyIds().contains(targetSubLevelId);
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

}
