package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// Register navigation control modes used by initialized SCMs
public final class ScmControlModeRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, ScmControlMode> MODES = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        ScmBuiltinControlModes.registerDefaults();
    }

    // Initialize the SCM control mode
    private ScmControlModeRegistry() {
    }

    // Register the SCM control mode
    public static synchronized void register(ScmControlMode mode) {
        ScmControlMode resolved = Objects.requireNonNull(mode, "mode");
        MODES.put(Objects.requireNonNull(resolved.id(), "mode.id"), resolved);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the SCM control mode
    public static synchronized void unregister(ResourceLocation id) {
        if (id != null && !ScmBuiltinControlModes.isBuiltin(id)) {
            MODES.remove(id);
        }
    }

    // Resolve the SCM control mode
    public static synchronized ScmControlMode resolve(@Nullable String id) {
        ResourceLocation parsed = parse(id);
        ScmControlMode mode = MODES.get(parsed);
        return mode == null ? MODES.get(ScmBuiltinControlModes.AIRSHIP_ID) : mode;
    }

    // Get the modes
    public static synchronized List<ScmControlMode> modes() {
        return List.copyOf(MODES.values());
    }

    // Get the serialized ids
    public static synchronized List<String> serializedIds() {
        return MODES.keySet().stream().map(ScmControlModeRegistry::serialize).toList();
    }

    // Get the serialize
    public static String serialize(ResourceLocation id) {
        return ScmBuiltinControlModes.NAMESPACE.equals(id.getNamespace())
                ? id.getPath() : id.toString();
    }

    // Parse the SCM control mode
    private static ResourceLocation parse(@Nullable String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return ScmBuiltinControlModes.AIRSHIP_ID;
        }
        ResourceLocation parsed = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(ScmBuiltinControlModes.NAMESPACE, normalized);
        return parsed == null ? ScmBuiltinControlModes.AIRSHIP_ID : parsed;
    }
}
