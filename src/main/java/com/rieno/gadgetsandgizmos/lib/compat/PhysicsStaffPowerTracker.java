package com.rieno.gadgetsandgizmos.lib.compat;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Store Physics Staff power by player so copied or replaced items cannot duplicate their charge
public final class PhysicsStaffPowerTracker extends SavedData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String FILE_ID = "createthrusters_physics_staff_power";
    private static final String STAFF_STATES_KEY = "StaffStates";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolved handler locks field
    private static Field handlerLocksField;
    // Resolved handler lock constructor
    private static Constructor<?> handlerLockConstructor;
    // Resolved handler lock remove method
    private static Method handlerLockRemoveMethod;
    // Tracked staff states
    private final Map<UUID, StaffState> staffStates = new HashMap<>();
    // Tracks whether physics staff power tracker is shutting down
    private boolean shuttingDown;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the physics staff power tracker value
    public static PhysicsStaffPowerTracker get(MinecraftServer server) {
        return (PhysicsStaffPowerTracker) server.overworld()
            .getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(PhysicsStaffPowerTracker::new,
                (CompoundTag tag, HolderLookup.Provider provider) -> PhysicsStaffPowerTracker.load(tag, provider), null), FILE_ID);
    }

    // Load the physics staff power tracker
    private static PhysicsStaffPowerTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PhysicsStaffPowerTracker tracker = new PhysicsStaffPowerTracker();
        ListTag states = tag.getList(STAFF_STATES_KEY, Tag.TAG_COMPOUND);
        for (Tag stateTag : states) {
            StaffState state = StaffState.load((CompoundTag) stateTag);
            if (state != null) {
                tracker.staffStates.put(state.staffId, state);
            }
        }
        return tracker;
    }

    // Check if this is locked
    public static boolean isLocked(ServerLevel level, UUID subLevelId) {
        SubLevel subLevel = getSubLevel(level, subLevelId);
        if (subLevel == null) {
            return false;
        }

        PhysicsStaffServerHandler.get(level).applyLockIfNeeded(subLevel);
        return PhysicsStaffServerHandler.get(level).isLocked(subLevel);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the lock toggled event
    public void onLockToggled(ServerLevel level, ServerPlayer player, UUID staffId, UUID subLevelId, boolean wasLockedBefore) {
        if (wasLockedBefore) {
            removeLockOwnership(level.dimension(), subLevelId);
            return;
        }

        removeLockOwnership(level.dimension(), subLevelId);
        StaffState state = staffStates.computeIfAbsent(staffId, ignored -> new StaffState(staffId, player.getUUID()));
        state.ownerId = player.getUUID();
        state.addLock(level.dimension(), subLevelId);
        setDirty();
    }

    // Handle the drag updated event
    public void onDragUpdated(ServerLevel level, ServerPlayer player, UUID staffId, UUID subLevelId) {
        StaffState state = staffStates.computeIfAbsent(staffId, ignored -> new StaffState(staffId, player.getUUID()));
        state.ownerId = player.getUUID();
        state.activeDrag = new AssemblyRef(level.dimension(), subLevelId);
    }

    // Get the locked count
    public int getLockedCount(UUID staffId) {
        StaffState state = staffStates.get(staffId);
        if (state == null) {
            return 0;
        }

        int lockedCount = 0;
        for (Set<UUID> locks : state.lockedAssemblies.values()) {
            lockedCount += locks.size();
        }
        return lockedCount;
    }

    // Clear the active drag
    public void clearActiveDrag(UUID playerId) {
        Iterator<StaffState> iterator = staffStates.values().iterator();
        while (iterator.hasNext()) {
            StaffState state = iterator.next();
            if (!state.ownerId.equals(playerId)) {
                continue;
            }
            state.activeDrag = null;
            if (state.isEmpty()) {
                iterator.remove();
            }
        }
    }

    // Update the physics staff power tracker
    public void tick(MinecraftServer server) {
        if (shuttingDown) {
            return;
        }

        boolean dirty = false;
        Iterator<Map.Entry<UUID, StaffState>> iterator = staffStates.entrySet().iterator();
        while (iterator.hasNext()) {
            StaffState state = iterator.next().getValue();
            if (!tickState(server, state)) {
                iterator.remove();
                dirty = true;
                continue;
            }
            dirty |= pruneMissingLocks(server, state);
        }

        if (dirty) {
            setDirty();
        }
    }

    // Begin the shutdown
    public void beginShutdown(MinecraftServer server) {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        for (StaffState state : staffStates.values()) {
            stopDragging(server, state);
            state.activeDrag = null;
            for (Map.Entry<ResourceKey<Level>, Set<UUID>> entry : state.lockedAssemblies.entrySet()) {
                ServerLevel level = server.getLevel(entry.getKey());
                if (level == null) {
                    continue;
                }

                PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
                for (UUID subLevelId : entry.getValue()) {
                    detachLiveLockHandle(handler, subLevelId);
                }
            }
        }
    }

    // Save the physics staff power tracker
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag states = new ListTag();
        for (StaffState state : staffStates.values()) {
            if (!state.lockedAssemblies.isEmpty()) {
                states.add(state.save());
            }
        }
        tag.put(STAFF_STATES_KEY, states);
        return tag;
    }

    // Update the state
    private boolean tickState(MinecraftServer server, StaffState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.ownerId);
        if (player == null) {

            state.activeDrag = null;
            return !state.lockedAssemblies.isEmpty();
        }

        ItemStack trackedStaff = PhysicsStaffPowerHooks.findPoweredStaffById(player, state.staffId);
        if (trackedStaff.isEmpty()) {
            releaseState(server, state);
            return false;
        }

        UUID currentHeldStaff = PhysicsStaffPowerHooks.getCurrentPoweredStaffId(player);
        if (state.activeDrag != null && !state.staffId.equals(currentHeldStaff)) {
            stopDragging(server, state);
            state.activeDrag = null;
        }

        if (state.activeDrag != null && getSubLevel(server, state.activeDrag) == null) {
            state.activeDrag = null;
        }

        if (state.isEmpty()) {
            return false;
        }

        if (!player.isCreative() && PhysicsStaffPowerHooks.getTotalAir(player) <= 0) {
            releaseState(server, state);
            return false;
        }

        double totalDrainPerSecond = getDrainPerSecond(server, state);
        if (totalDrainPerSecond <= 0.0D) {
            return true;
        }

        state.pendingAirDrain += totalDrainPerSecond / 20.0D;
        int airCost = (int) Math.floor(state.pendingAirDrain);
        if (airCost <= 0) {
            return true;
        }

        state.pendingAirDrain -= airCost;
        if (!PhysicsStaffPowerHooks.consumeAir(player, airCost) || PhysicsStaffPowerHooks.getTotalAir(player) <= 0) {
            releaseState(server, state);
            return false;
        }

        return true;
    }

    // Prune missing staff locks
    private boolean pruneMissingLocks(MinecraftServer server, StaffState state) {
        boolean changed = false;
        Iterator<Map.Entry<ResourceKey<Level>, Set<UUID>>> mapIterator = state.lockedAssemblies.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, Set<UUID>> entry = mapIterator.next();
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                mapIterator.remove();
                changed = true;
                continue;
            }

            Iterator<UUID> locks = entry.getValue().iterator();
            PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
            while (locks.hasNext()) {
                UUID subLevelId = locks.next();
                SubLevel subLevel = getSubLevel(level, subLevelId);
                if (subLevel == null) {
                    locks.remove();
                    changed = true;
                    continue;
                }

                handler.applyLockIfNeeded(subLevel);
                if (!handler.isLocked(subLevel)) {
                    locks.remove();
                    changed = true;
                }
            }

            if (entry.getValue().isEmpty()) {
                mapIterator.remove();
            }
        }

        return changed;
    }

    // Get the drain per second
    private double getDrainPerSecond(MinecraftServer server, StaffState state) {
        double drainPerSecond = 0.0D;

        if (state.activeDrag != null) {
            ServerLevel level = server.getLevel(state.activeDrag.dimension);
            if (level != null) {
                double mass = PhysicsStaffPowerHooks.getAssemblyMass(level, state.activeDrag.subLevelId);
                drainPerSecond += PhysicsStaffPowerHooks.getDragDrainPerSecond(mass);
            }
        }

        for (Map.Entry<ResourceKey<Level>, Set<UUID>> entry : state.lockedAssemblies.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (UUID subLevelId : entry.getValue()) {
                double mass = PhysicsStaffPowerHooks.getAssemblyMass(level, subLevelId);
                drainPerSecond += PhysicsStaffPowerHooks.getLockDrainPerSecond(mass);
            }
        }

        return drainPerSecond;
    }

    // Release the state
    private void releaseState(MinecraftServer server, StaffState state) {
        stopDragging(server, state);
        for (Map.Entry<ResourceKey<Level>, Set<UUID>> entry : state.lockedAssemblies.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
            for (UUID subLevelId : entry.getValue()) {
                SubLevel subLevel = getSubLevel(level, subLevelId);
                if (subLevel != null && handler.isLocked(subLevel)) {
                    handler.removeLock(subLevel);
                }
            }
        }
        state.lockedAssemblies.clear();
        state.activeDrag = null;
    }

    // Stop the dragging
    private void stopDragging(MinecraftServer server, StaffState state) {
        if (state.activeDrag == null) {
            return;
        }
        ServerLevel level = server.getLevel(state.activeDrag.dimension);
        if (level != null) {
            PhysicsStaffServerHandler.get(level).stopDragging(state.ownerId);
        }
    }

    // Remove the lock ownership
    private void removeLockOwnership(ResourceKey<Level> dimension, UUID subLevelId) {
        boolean changed = false;
        Iterator<StaffState> iterator = staffStates.values().iterator();
        while (iterator.hasNext()) {
            StaffState state = iterator.next();
            if (!state.removeLock(dimension, subLevelId)) {
                continue;
            }
            changed = true;
            if (state.isEmpty()) {
                iterator.remove();
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // Detach the live lock handle
    private static void detachLiveLockHandle(PhysicsStaffServerHandler handler, UUID subLevelId) {
        try {
            Map<UUID, Object> locks = getHandlerLocks(handler);
            Object lock = locks.get(subLevelId);
            if (lock == null) {
                return;
            }

            getHandlerLockRemoveMethod(lock.getClass()).invoke(lock);
            locks.put(subLevelId, getHandlerLockConstructor(lock.getClass()).newInstance(subLevelId, null));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Get the handler locks
    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> getHandlerLocks(PhysicsStaffServerHandler handler) throws ReflectiveOperationException {
        if (handlerLocksField == null) {
            handlerLocksField = PhysicsStaffServerHandler.class.getDeclaredField("locks");
            handlerLocksField.setAccessible(true);
        }
        return (Map<UUID, Object>) handlerLocksField.get(handler);
    }

    // Get the handler lock constructor
    private static Constructor<?> getHandlerLockConstructor(Class<?> lockClass) throws ReflectiveOperationException {
        if (handlerLockConstructor == null) {
            Constructor<?> constructor = lockClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            handlerLockConstructor = constructor;
        }
        return handlerLockConstructor;
    }

    // Get the handler lock remove method
    private static Method getHandlerLockRemoveMethod(Class<?> lockClass) throws ReflectiveOperationException {
        if (handlerLockRemoveMethod == null) {
            handlerLockRemoveMethod = lockClass.getDeclaredMethod("remove");
            handlerLockRemoveMethod.setAccessible(true);
        }
        return handlerLockRemoveMethod;
    }

    // Get the sublevel
    private static SubLevel getSubLevel(MinecraftServer server, AssemblyRef ref) {
        ServerLevel level = server.getLevel(ref.dimension);
        if (level == null) {
            return null;
        }
        return getSubLevel(level, ref.subLevelId);
    }

    // Get the sublevel
    private static SubLevel getSubLevel(ServerLevel level, UUID subLevelId) {
        Object subLevel = SubLevelContainer.getContainer(level).getSubLevel(subLevelId);
        if (subLevel instanceof SubLevel sableSubLevel) {
            return sableSubLevel;
        }
        return null;
    }

    // Store staff state
    private static final class StaffState {
        // Staff id
        private final UUID staffId;
        // Current owner id
        private UUID ownerId;
        // Tracked locked assemblies
        private final Map<ResourceKey<Level>, Set<UUID>> lockedAssemblies = new HashMap<>();
        // Active drag
        private AssemblyRef activeDrag;
        // Pending air drain
        private double pendingAirDrain;

        // Initialize the staff state
        private StaffState(UUID staffId, UUID ownerId) {
            this.staffId = staffId;
            this.ownerId = ownerId;
        }

        // Load the staff state
        private static StaffState load(CompoundTag tag) {
            if (!tag.hasUUID("StaffId") || !tag.hasUUID("OwnerId")) {
                return null;
            }

            StaffState state = new StaffState(tag.getUUID("StaffId"), tag.getUUID("OwnerId"));
            state.pendingAirDrain = tag.getDouble("PendingAirDrain");
            ListTag locks = tag.getList("Locks", Tag.TAG_COMPOUND);
            for (Tag lockTag : locks) {
                CompoundTag lockData = (CompoundTag) lockTag;
                ResourceLocation dimensionId = ResourceLocation.parse(lockData.getString("Dimension"));
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                if (!lockData.hasUUID("SubLevelId")) {
                    continue;
                }
                state.addLock(dimension, lockData.getUUID("SubLevelId"));
            }
            return state;
        }

        // Save the staff state
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("StaffId", staffId);
            tag.putUUID("OwnerId", ownerId);
            tag.putDouble("PendingAirDrain", pendingAirDrain);
            ListTag locks = new ListTag();
            for (Map.Entry<ResourceKey<Level>, Set<UUID>> entry : lockedAssemblies.entrySet()) {
                for (UUID subLevelId : entry.getValue()) {
                    CompoundTag lockData = new CompoundTag();
                    lockData.putString("Dimension", entry.getKey().location().toString());
                    lockData.put("SubLevelId", NbtUtils.createUUID(subLevelId));
                    locks.add(lockData);
                }
            }
            tag.put("Locks", locks);
            return tag;
        }

        // Add the lock
        private void addLock(ResourceKey<Level> dimension, UUID subLevelId) {
            lockedAssemblies.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(subLevelId);
        }

        // Remove the lock
        private boolean removeLock(ResourceKey<Level> dimension, UUID subLevelId) {
            Set<UUID> assemblies = lockedAssemblies.get(dimension);
            if (assemblies == null || !assemblies.remove(subLevelId)) {
                return false;
            }
            if (assemblies.isEmpty()) {
                lockedAssemblies.remove(dimension);
            }
            return true;
        }

        // Check if this is empty
        private boolean isEmpty() {
            return activeDrag == null && lockedAssemblies.isEmpty();
        }
    }

    // Handle the assembly ref
    private static final class AssemblyRef {
        // Dimension
        private final ResourceKey<Level> dimension;
        // Sub-level id
        private final UUID subLevelId;

        // Initialize the assembly ref
        private AssemblyRef(ResourceKey<Level> dimension, UUID subLevelId) {
            this.dimension = dimension;
            this.subLevelId = subLevelId;
        }
    }
}
