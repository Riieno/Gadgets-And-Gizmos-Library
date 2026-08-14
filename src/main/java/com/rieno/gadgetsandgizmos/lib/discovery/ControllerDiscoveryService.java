package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Find stable controller targets across loaded root and Sable sub-level block entities
public final class ControllerDiscoveryService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String CUSTOM_LABEL_PERSISTENT_KEY = "CreateThrustersControllerLabel";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller discovery service
    private ControllerDiscoveryService() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scan the block entities
    public static List<ControllerDiscoveryNode> scanBlockEntities(Iterable<BlockEntity> blockEntities,
                                                                  @Nullable UUID subLevelId,
                                                                  String groupId) {
        Map<String, ControllerDiscoveryNode> nodes = new LinkedHashMap<>();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity == null || blockEntity.isRemoved()) {
                continue;
            }

            ControllerDiscoveryNode node = classify(blockEntity, subLevelId, groupId);
            if (node == null || !node.isValid()) {
                continue;
            }
            nodes.putIfAbsent(node.nodeId(), node);
        }
        return new ArrayList<>(nodes.values());
    }

    // Classify a controller block entity
    public static @Nullable ControllerDiscoveryNode classify(BlockEntity blockEntity, @Nullable UUID subLevelId,
                                                             String groupId) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (blockId == null) {
            return null;
        }

        ControllerDiscoveryKind kind = classifyKind(blockId);
        if (kind == ControllerDiscoveryKind.UNKNOWN) {
            return null;
        }

        BlockPos blockPos = blockEntity.getBlockPos().immutable();
        String nodeId = buildNodeId(blockId, blockPos, subLevelId);
        String rawLabel = extractCustomLabel(blockEntity);
        if (rawLabel == null || rawLabel.isBlank()) {
            rawLabel = buildLabel(blockId);
        }
        return new ControllerDiscoveryNode(nodeId, kind, normalizeGroupId(groupId, subLevelId), blockId.toString(),
                rawLabel, subLevelId, blockPos);
    }

    // Extract the custom label
    private static @Nullable String extractCustomLabel(BlockEntity blockEntity) {
        if (blockEntity.getPersistentData().contains(CUSTOM_LABEL_PERSISTENT_KEY)) {
            String persistentLabel = blockEntity.getPersistentData().getString(CUSTOM_LABEL_PERSISTENT_KEY);
            if (!persistentLabel.isBlank()) {
                return persistentLabel.strip();
            }
        }

        if (blockEntity instanceof INamedBlockEntity named) {
            String customName = named.getCustomName();
            if (customName != null && !customName.isBlank()) {
                return customName.strip();
            }
        }

        String reflected = invokeStringNameGetter(blockEntity, "getCustomName");
        if (reflected != null && !reflected.isBlank()) {
            return reflected.strip();
        }

        reflected = invokeStringNameGetter(blockEntity, "getName");
        if (reflected != null && !reflected.isBlank()) {
            return reflected.strip();
        }

        return null;
    }

    // Run the string name getter
    private static @Nullable String invokeStringNameGetter(BlockEntity blockEntity, String methodName) {
        try {
            Method method = blockEntity.getClass().getMethod(methodName);
            Object val = method.invoke(blockEntity);
            if (val instanceof Component component) {
                String text = component.getString();
                return text == null || text.isBlank() ? null : text;
            }
            if (val instanceof String text) {
                return text.isBlank() ? null : text;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    // Get the classify kind
    public static ControllerDiscoveryKind classifyKind(ResourceLocation blockId) {
        String namespace = blockId.getNamespace();
        String path = blockId.getPath();

        if ("createthrusters".equals(namespace)) {
            if (path.endsWith("double_button")) {
                return ControllerDiscoveryKind.DOUBLE_BUTTON;
            }
            if (path.contains("gyroscope_link")) {
                return ControllerDiscoveryKind.GYROSCOPE_LINK;
            }
            if (path.contains("vector_bearing")) {
                return ControllerDiscoveryKind.VECTOR_BEARING;
            }
            if (path.contains("thruster_bearing")) {
                return ControllerDiscoveryKind.BEARING;
            }
            if (path.contains("aileron_bearing")) {
                return ControllerDiscoveryKind.BEARING;
            }
            if (path.equals("thruster")) {
                return ControllerDiscoveryKind.THRUSTER;
            }
            if (path.contains("analogue_joystick")) {
                return ControllerDiscoveryKind.JOYSTICK;
            }
            if (path.equals("claw")) {
                return ControllerDiscoveryKind.CLAW;
            }
        }

        if (path.contains("redstone_link")) {
            return ControllerDiscoveryKind.REDSTONE_LINK;
        }

        if (path.contains("encased_fan") || path.equals("fan")) {
            return ControllerDiscoveryKind.FAN;
        }
        if (path.contains("analog_lever")) {
            return ControllerDiscoveryKind.ANALOG_LEVER;
        }
        if (path.contains("throttle_lever")) {
            return ControllerDiscoveryKind.THROTTLE;
        }
        if (path.contains("analog_transmission")) {
            return ControllerDiscoveryKind.ANALOG_TRANSMISSION;
        }
        if (path.contains("steering_wheel")) {
            return ControllerDiscoveryKind.STEERING_WHEEL;
        }

        if (path.contains("wheel_mount")) {
            return ControllerDiscoveryKind.WHEEL_MOUNT;
        }
        if (path.contains("navigation_table")) {
            return ControllerDiscoveryKind.NAVIGATION_TABLE;
        }
        if (path.contains("gimbal_sensor")) {
            return ControllerDiscoveryKind.GIMBAL_SENSOR;
        }
        if (path.contains("magnet")) {
            return ControllerDiscoveryKind.MAGNET;
        }

        return ControllerDiscoveryKind.UNKNOWN;
    }

    // Build the node id
    private static String buildNodeId(ResourceLocation blockId, BlockPos blockPos, @Nullable UUID subLevelId) {
        String suffix = subLevelId == null ? "world" : subLevelId.toString();
        return blockId + "@" + blockPos.asLong() + "#" + suffix;
    }

    // Normalize the group id
    private static String normalizeGroupId(String groupId, @Nullable UUID subLevelId) {
        if (groupId != null && !groupId.isBlank()) {
            return groupId.trim();
        }
        return subLevelId == null ? "world" : "sublevel:" + subLevelId;
    }

    // Build the label
    private static String buildLabel(ResourceLocation blockId) {
        String[] parts = blockId.getPath().split("_");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            label.append(part.substring(1));
        }
        return label.toString();
    }
}
