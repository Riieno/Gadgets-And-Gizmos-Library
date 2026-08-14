package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.config.GadgetsNGizmosLibraryConfigs;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

// Keep the library's global kinetic patches away from incompatible block entities
public final class GadgetsNGizmosKineticGuard {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SIMULATED_SWIVEL_PACKAGE =
            "dev.simulated_team.simulated.content.blocks.swivel_bearing";
    private static final Set<String> LOGGED_GUARD_KEYS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Predicate<KineticBlockEntity>> GUARD_EXCEPTIONS =
            new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Gadgets & Gizmos kinetic guard
    private GadgetsNGizmosKineticGuard() {
    }

    // Allow one keyed kinetic predicate through guards which would normally block it
    public static void registerGuardException(String key, Predicate<KineticBlockEntity> predicate) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Guard exception keys cannot be blank");
        }
        GUARD_EXCEPTIONS.put(key.strip(), Objects.requireNonNull(predicate, "predicate"));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove a previously registered guard exception
    public static boolean unregisterGuardException(String key) {
        return key != null && !key.isBlank() && GUARD_EXCEPTIONS.remove(key.strip()) != null;
    }

    // Check if this should apply held angle
    public static boolean shouldApplyHeldAngleTo(KineticBlockEntity blockEntity) {
        if (!GadgetsNGizmosLibraryConfigs.enableGlobalHeldAngleMixin()) {
            logGuardDecisionOnce("held-angle-disabled:" + blockEntity.getClass().getName(),
                    "[gadgetsngizmos][KineticGuard] Held-angle mixin disabled globally for {}",
                    blockEntity.getClass().getName());
            return false;
        }
        return !isSimulatedSwivelBearingKinetic(blockEntity, "held-angle");
    }

    // Check if this should propagate precise angle
    public static boolean shouldPropagatePreciseAngleTo(KineticBlockEntity blockEntity) {
        if (!GadgetsNGizmosLibraryConfigs.enableGlobalPreciseAnglePropagation()) {
            return false;
        }
        return !isSimulatedSwivelBearingKinetic(blockEntity, "precise-angle");
    }

    // Check if this should use virtual kinetic
    public static boolean shouldUseVirtualKineticFor(KineticBlockEntity blockEntity, BlockPos neighborPos) {
        if (!GadgetsNGizmosLibraryConfigs.enableGlobalVirtualKineticPropagation()) {
            return false;
        }
        return !isSimulatedSwivelBearingKinetic(blockEntity, "virtual-kinetic@" + neighborPos);
    }

    // Check if this is a simulated swivel bearing kinetic
    private static boolean isSimulatedSwivelBearingKinetic(KineticBlockEntity blockEntity, String channel) {
        String className = blockEntity.getClass().getName();
        if (!className.startsWith(SIMULATED_SWIVEL_PACKAGE)) {
            return false;
        }
        if (isGuardException(blockEntity)) {
            return false;
        }

        logGuardDecisionOnce(channel + ":" + className,
                "[gadgetsngizmos][KineticGuard] Blocking {} for {}", channel, className);
        return true;
    }

    // Log the guard decision once
    private static void logGuardDecisionOnce(String key, String msg, Object... args) {
        if (GadgetsNGizmosLibraryConfigs.logKineticGuardDecisions()
                && LOGGED_GUARD_KEYS.add(key)) {
            LOGGER.info(msg, args);
        }
    }

    // Check if this is a guard exception
    private static boolean isGuardException(KineticBlockEntity blockEntity) {
        for (Map.Entry<String, Predicate<KineticBlockEntity>> entry : GUARD_EXCEPTIONS.entrySet()) {
            try {
                if (entry.getValue().test(blockEntity)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError err) {
                logGuardDecisionOnce("guard-exception-failed:" + entry.getKey(),
                        "[gadgetsngizmos][KineticGuard] Guard exception {} failed for {}",
                        entry.getKey(), blockEntity.getClass().getName());
            }
        }
        return false;
    }
}
