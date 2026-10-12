package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.compat.MixinCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Load optional library mixins only when their target mods and classes are available
public final class GadgetsNGizmosLibraryMixinConfigPlugin implements IMixinConfigPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean LOGGED_HELD_DISABLE = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_PRECISE_DISABLE = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_VIRTUAL_DISABLE = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_AEROWORKS_COMPAT = new AtomicBoolean(false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the load event
    @Override
    public void onLoad(String mixinPackage) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ref mapper config
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    // Check if this should apply mixin
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if("com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneEntityCullingMixin".equals(mixinClassName)){
            return MixinCompatibility.isModLoaded("entityculling");
        }
        if(targetClassName.startsWith("net.caffeinemc.mods.sodium.")) return MixinCompatibility.canApply(targetClassName, "sodium");
        if(targetClassName.startsWith("net.irisshaders.iris.")) return MixinCompatibility.canApply(targetClassName, "iris");
        if(targetClassName.startsWith("me.cortex.voxy.")) return MixinCompatibility.canApply(targetClassName, "voxy");
        if(targetClassName.startsWith("com.seibel.distanthorizons.")) return MixinCompatibility.canApply(targetClassName, "distanthorizons");
        // -----------------------------------------------------OPTIONAL COMPAT---------------------------------------------------
        if ("com.rieno.gadgetsandgizmos.lib.mixin.AeroworksServoPreciseAngleOutputMixin".equals(mixinClassName)) {
            boolean loaded = MixinCompatibility.canApply(targetClassName, "aeroworks");
            if (loaded && LOGGED_AEROWORKS_COMPAT.compareAndSet(false, true)) {
                LOGGER.info("[GadgetsNGizmos][Compat] Enabled Aeroworks precise-angle compatibility");
            }
            return loaded;
        }

        // -----------------------------------------------------HELD ANGLE OPT OUT------------------------------------------------
        if (isDisabled("disableHeldAngleMixins")
                && "com.rieno.gadgetsandgizmos.lib.mixin.KineticBlockEntityHeldAngleMixin".equals(mixinClassName)) {
            if (LOGGED_HELD_DISABLE.compareAndSet(false, true)) {
                LOGGER.warn("[GadgetsNGizmos][Mixin] Disabled KineticBlockEntityHeldAngleMixin");
            }
            return false;
        }

        // -----------------------------------------------------PRECISE ANGLE OPT OUT---------------------------------------------
        if (isDisabled("disablePreciseAngleMixins")
                && ("com.rieno.gadgetsandgizmos.lib.mixin.KineticBlockEntityPreciseAngleOutputMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.HandCrankPreciseAngleOutputMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.ValveHandlePreciseAngleOutputMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.SteeringWheelPreciseAngleOutputMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.SequencedGearshiftPreciseAngleOutputMixin".equals(mixinClassName))) {
            if (LOGGED_PRECISE_DISABLE.compareAndSet(false, true)) {
                LOGGER.warn("[GadgetsNGizmos][Mixin] Disabled precise-angle mixins");
            }
            return false;
        }

        // -----------------------------------------------------VIRTUAL KINETIC OPT OUT-------------------------------------------
        if (isDisabled("disableVirtualKineticMixins")
                && ("com.rieno.gadgetsandgizmos.lib.mixin.VirtualKineticBlockEntityMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.VirtualKineticNetworkMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.lib.mixin.VirtualKineticRotationPropagatorMixin".equals(mixinClassName))) {
            if (LOGGED_VIRTUAL_DISABLE.compareAndSet(false, true)) {
                LOGGER.warn("[GadgetsNGizmos][Mixin] Disabled virtual-kinetic mixins");
            }
            return false;
        }

        return true;
    }

    // Check the current and legacy system property names
    private static boolean isDisabled(String option) {
        return Boolean.getBoolean("gadgetsngizmos." + option)
                || Boolean.getBoolean("ct." + option);
    }

    // Accept the targets
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Get the mixins
    @Override
    public List<String> getMixins() {
        return null;
    }

    // Prepare a library mixin application
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // Post the apply
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
