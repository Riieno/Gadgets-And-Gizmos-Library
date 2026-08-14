package com.rieno.gadgetsandgizmos.lib.compat;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelTelemetryApi;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

// Expose small installable hooks so the main addon can supply Physics Staff power storage
public final class PhysicsStaffPowerHooks {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int USES_PER_TANK = 1024;
    private static final String STAFF_ID_KEY = "PhysicsStaffId";
    private static final String LOCKED_COUNT_KEY = "LockedCount";
    private static final String PRESSURE_CURRENT_KEY = "PressureCurrent";
    private static final String PRESSURE_MAX_KEY = "PressureMax";
    private static final ResourceLocation POWERED_STAFF_ID = ResourceLocation.fromNamespaceAndPath("createthrusters", "physics_staff");
    private static final double DRAG_DRAIN_BASE = 0.25D;
    private static final double DRAG_DRAIN_SCALE = 0.12D;
    private static final double LOCK_DRAIN_BASE = 0.10D;
    private static final double LOCK_DRAIN_SCALE = 0.05D;

    // Define the staff action failure values
    public enum StaffActionFailure {
        NONE,
        MISSING_BACKTANK,
        OUT_OF_PRESSURE
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics staff power hooks
    private PhysicsStaffPowerHooks() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the physics staff is powered
    public static boolean isPoweredPhysicsStaff(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(POWERED_STAFF_ID);
    }

    // Check if the player is holding a powered physics staff
    public static boolean isHoldingPoweredPhysicsStaff(Player player) {
        return !getCurrentPoweredStaff(player).isEmpty();
    }

    // Get the current powered staff
    public static ItemStack getCurrentPoweredStaff(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem) {
            return isPoweredPhysicsStaff(mainHand) ? mainHand : ItemStack.EMPTY;
        }

        ItemStack offHand = player.getOffhandItem();
        if (isPoweredPhysicsStaff(offHand)) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    // Get the current powered staff id
    public static UUID getCurrentPoweredStaffId(Player player) {
        ItemStack stack = getCurrentPoweredStaff(player);
        if (stack.isEmpty()) {
            return null;
        }
        return getOrCreateStaffId(stack);
    }

    // Find the powered staff by id
    public static ItemStack findPoweredStaffById(Player player, UUID staffId) {
        if (staffId == null) {
            return ItemStack.EMPTY;
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isPoweredPhysicsStaff(stack)) {
                continue;
            }
            UUID stackId = getStaffId(stack);
            if (staffId.equals(stackId)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    // Get or create the staff id
    public static UUID getOrCreateStaffId(ItemStack stack) {
        if (!isPoweredPhysicsStaff(stack)) {
            return null;
        }

        UUID existing = getStaffId(stack);
        if (existing != null) {
            return existing;
        }

        CompoundTag tag = ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        UUID created = UUID.randomUUID();
        tag.putUUID(STAFF_ID_KEY, created);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return created;
    }

    // Get the staff id
    public static UUID getStaffId(ItemStack stack) {
        if (!isPoweredPhysicsStaff(stack)) {
            return null;
        }

        CompoundTag tag = ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        if (!tag.hasUUID(STAFF_ID_KEY)) {
            return null;
        }
        return tag.getUUID(STAFF_ID_KEY);
    }

    // Check if this has air
    public static boolean hasAir(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return player.isCreative() || getEquippedBacktankAir(player) > 0;
    }

    // Get the action failure
    public static StaffActionFailure getActionFailure(Player player) {
        if (player.isCreative()) {
            return StaffActionFailure.NONE;
        }

        ItemStack equippedBacktank = getEquippedBacktank(player);
        if (equippedBacktank.isEmpty()) {
            return StaffActionFailure.MISSING_BACKTANK;
        }

        return BacktankUtil.getAir(equippedBacktank) > 0
                ? StaffActionFailure.NONE
                : StaffActionFailure.OUT_OF_PRESSURE;
    }

    // Authorize a physics staff action
    public static boolean authorizeAction(Player player, boolean showFailureMessage) {
        StaffActionFailure failure = getActionFailure(player);
        if (failure == StaffActionFailure.NONE) {
            return true;
        }

        if (showFailureMessage) {
            player.displayClientMessage(getFailureMessage(failure), true);
        }
        return false;
    }

    // Consume the air
    public static boolean consumeAir(LivingEntity entity) {
        return BacktankUtil.canAbsorbDamage(entity, USES_PER_TANK);
    }

    // Consume the air
    public static boolean consumeAir(LivingEntity entity, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (entity instanceof Player player && player.isCreative()) {
            return true;
        }

        ItemStack equippedBacktank = getEquippedBacktank(entity);
        if (equippedBacktank.isEmpty()) {
            return false;
        }

        int available = BacktankUtil.getAir(equippedBacktank);
        if (available <= 0) {
            return false;
        }
        int drain = Math.min(available, amount);
        BacktankUtil.consumeAir(entity, equippedBacktank, drain);
        return drain >= amount;
    }

    // Get the total air
    public static int getTotalAir(LivingEntity entity) {
        if (entity instanceof Player player && player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        return getEquippedBacktankAir(entity);
    }

    // Get the total air capacity
    public static int getTotalAirCapacity(LivingEntity entity) {
        if (entity instanceof Player player && player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        ItemStack equippedBacktank = getEquippedBacktank(entity);
        return equippedBacktank.isEmpty() ? 0 : BacktankUtil.maxAir(equippedBacktank);
    }

    // Get the equipped backtank air
    private static int getEquippedBacktankAir(LivingEntity entity) {
        ItemStack equippedBacktank = getEquippedBacktank(entity);
        return equippedBacktank.isEmpty() ? 0 : BacktankUtil.getAir(equippedBacktank);
    }

    // Get the equipped backtank
    private static ItemStack getEquippedBacktank(LivingEntity entity) {
        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(entity);
        return backtanks.isEmpty() ? ItemStack.EMPTY : backtanks.getFirst();
    }

    // Get the tooltip pressure current
    public static int getTooltipPressureCurrent(Player player) {
        return player.isCreative() ? -1 : getTotalAir(player);
    }

    // Get the tooltip pressure capacity
    public static int getTooltipPressureCapacity(Player player) {
        return player.isCreative() ? -1 : getTotalAirCapacity(player);
    }

    // Update the tooltip snapshot
    public static void updateTooltipSnapshot(ItemStack stack, int lockedCount, int pressureCurrent, int pressureMax) {
        if (!isPoweredPhysicsStaff(stack)) {
            return;
        }

        CompoundTag tag = ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        boolean changed = putIntIfChanged(tag, LOCKED_COUNT_KEY, lockedCount);
        changed |= putIntIfChanged(tag, PRESSURE_CURRENT_KEY, pressureCurrent);
        changed |= putIntIfChanged(tag, PRESSURE_MAX_KEY, pressureMax);
        if (changed) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    // Get the stored locked count
    public static int getStoredLockedCount(ItemStack stack) {
        return getStoredInt(stack, LOCKED_COUNT_KEY, 0);
    }

    // Get the stored pressure current
    public static int getStoredPressureCurrent(ItemStack stack) {
        return getStoredInt(stack, PRESSURE_CURRENT_KEY, 0);
    }

    // Get the stored pressure max
    public static int getStoredPressureMax(ItemStack stack) {
        return getStoredInt(stack, PRESSURE_MAX_KEY, 0);
    }

    // Get the drag drain per second
    public static double getDragDrainPerSecond(double mass) {
        double weightedLoad = Math.max(1.0D, Math.sqrt(Math.max(mass, 0.0D)));
        return DRAG_DRAIN_BASE + weightedLoad * DRAG_DRAIN_SCALE;
    }

    // Get the lock drain per second
    public static double getLockDrainPerSecond(double mass) {
        double weightedLoad = Math.max(1.0D, Math.sqrt(Math.max(mass, 0.0D)));
        return LOCK_DRAIN_BASE + weightedLoad * LOCK_DRAIN_SCALE;
    }

    // Get the assembly mass
    public static double getAssemblyMass(ServerLevel level, UUID subLevelId) {
        return SableSubLevelTelemetryApi.sample(level, subLevelId).mass();
    }

    // Check if the bar is visible
    public static boolean isBarVisible(ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, USES_PER_TANK);
    }

    // Get the bar width
    public static int getBarWidth(ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, USES_PER_TANK);
    }

    // Get the bar color
    public static int getBarColor(ItemStack stack) {
        return BacktankUtil.getBarColor(stack, USES_PER_TANK);
    }

    // Get the failure message
    private static Component getFailureMessage(StaffActionFailure failure) {
        return switch (failure) {
            case MISSING_BACKTANK -> Component.translatable("item.createthrusters.physics_staff.error.no_backtank");
            case OUT_OF_PRESSURE -> Component.translatable("item.createthrusters.physics_staff.error.no_pressure");
            case NONE -> Component.empty();
        };
    }

    // Get the stored int
    private static int getStoredInt(ItemStack stack, String key, int fallback) {
        if (!isPoweredPhysicsStaff(stack)) {
            return fallback;
        }

        CompoundTag tag = ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    // Put the int if changed
    private static boolean putIntIfChanged(CompoundTag tag, String key, int val) {
        if (tag.contains(key, Tag.TAG_INT) && tag.getInt(key) == val) {
            return false;
        }
        tag.putInt(key, val);
        return true;
    }
}
