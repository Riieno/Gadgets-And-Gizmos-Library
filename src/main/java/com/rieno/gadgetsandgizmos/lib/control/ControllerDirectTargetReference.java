package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Store one direct controller target across root and moving Sable levels
public final class ControllerDirectTargetReference {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Target id
    private final String targetId;
    // Target type id
    private final String targetTypeId;
    // Group id
    private final String groupId;
    // Display label
    private final String label;
    // Compat mode id
    private final String compatModeId;
    // Sub-level id
    private final @Nullable UUID subLevelId;
    // Block position
    private final @Nullable BlockPos blockPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller direct target reference
    public ControllerDirectTargetReference(String targetId, String targetTypeId, String groupId, String label,
                                           @Nullable UUID subLevelId, @Nullable BlockPos blockPos) {
        this(targetId, targetTypeId, groupId, label, "", subLevelId, blockPos);
    }

    // Initialize the controller direct target reference
    public ControllerDirectTargetReference(String targetId, String targetTypeId, String groupId, String label,
                                           String compatModeId, @Nullable UUID subLevelId, @Nullable BlockPos blockPos) {
        this.targetId = normalize(targetId);
        this.targetTypeId = normalize(targetTypeId);
        this.groupId = normalize(groupId);
        this.label = normalize(label);
        this.compatModeId = normalize(compatModeId);
        this.subLevelId = subLevelId;
        this.blockPos = blockPos == null ? null : blockPos.immutable();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the target id
    public String targetId() {
        return targetId;
    }

    // Get the target type id
    public String targetTypeId() {
        return targetTypeId;
    }

    // Get the group id
    public String groupId() {
        return groupId;
    }

    // Get the label
    public String label() {
        return label;
    }

    // Get the compat mode id
    public String compatModeId() {
        return compatModeId;
    }

    // Get the sublevel id
    public @Nullable UUID subLevelId() {
        return subLevelId;
    }

    // Get the block pos
    public @Nullable BlockPos blockPos() {
        return blockPos == null ? null : blockPos.immutable();
    }

    // Check if this is bound
    public boolean isBound() {
        return !targetId.isEmpty();
    }

    // Write the controller direct target reference data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (!targetId.isEmpty()) {
            tag.putString("TargetId", targetId);
        }
        if (!targetTypeId.isEmpty()) {
            tag.putString("TargetTypeId", targetTypeId);
        }
        if (!groupId.isEmpty()) {
            tag.putString("GroupId", groupId);
        }
        if (!label.isEmpty()) {
            tag.putString("Label", label);
        }
        if (!compatModeId.isEmpty()) {
            tag.putString("CompatModeId", compatModeId);
        }
        if (subLevelId != null) {
            tag.putUUID("SubLevelId", subLevelId);
        }
        if (blockPos != null) {
            tag.putLong("BlockPos", blockPos.asLong());
        }
        return tag;
    }

    // Read the controller direct target reference data
    public static @Nullable ControllerDirectTargetReference fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        String targetId = tag.getString("TargetId");
        if (targetId.isEmpty()) {
            return null;
        }
        UUID subLevelId = tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : null;
        BlockPos blockPos = tag.contains("BlockPos") ? BlockPos.of(tag.getLong("BlockPos")) : null;
        return new ControllerDirectTargetReference(
                targetId,
                tag.getString("TargetTypeId"),
                tag.getString("GroupId"),
                tag.getString("Label"),
                tag.getString("CompatModeId"),
                subLevelId,
                blockPos);
    }

    // Copy the controller direct target reference with the compat mode
    public ControllerDirectTargetReference withCompatMode(String nextCompatModeId) {
        String normalized = normalize(nextCompatModeId);
        if (compatModeId.equals(normalized)) {
            return this;
        }
        return new ControllerDirectTargetReference(
                targetId,
                targetTypeId,
                groupId,
                label,
                normalized,
                subLevelId,
                blockPos);
    }

    // Compare this controller direct target reference with another object
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ControllerDirectTargetReference reference)) {
            return false;
        }
        return targetId.equals(reference.targetId)
                && targetTypeId.equals(reference.targetTypeId)
                && groupId.equals(reference.groupId)
                && label.equals(reference.label)
                && compatModeId.equals(reference.compatModeId)
                && Objects.equals(subLevelId, reference.subLevelId)
                && Objects.equals(blockPos, reference.blockPos);
    }

    // Generate the controller direct target reference hash
    @Override
    public int hashCode() {
        return Objects.hash(targetId, targetTypeId, groupId, label, compatModeId, subLevelId, blockPos);
    }

    // Normalize the controller direct target reference
    private static String normalize(String val) {
        return val == null ? "" : val.trim();
    }
}
