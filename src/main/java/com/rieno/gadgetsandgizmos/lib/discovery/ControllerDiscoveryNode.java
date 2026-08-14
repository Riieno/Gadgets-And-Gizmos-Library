package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Store one normalized discovery result and its direct controller target
public final class ControllerDiscoveryNode {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Node id
    private final String nodeId;
    // Kind
    private final ControllerDiscoveryKind kind;
    // Group id
    private final String groupId;
    // Block id
    private final String blockId;
    // Display label
    private final String label;
    // Sub-level id
    private final @Nullable UUID subLevelId;
    // Block position
    private final @Nullable BlockPos blockPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller discovery node
    public ControllerDiscoveryNode(String nodeId, ControllerDiscoveryKind kind, String groupId, String blockId,
                                   String label, @Nullable UUID subLevelId, @Nullable BlockPos blockPos) {
        this.nodeId = normalize(nodeId);
        this.kind = kind == null ? ControllerDiscoveryKind.UNKNOWN : kind;
        this.groupId = normalize(groupId);
        this.blockId = normalize(blockId);
        this.label = normalize(label);
        this.subLevelId = subLevelId;
        this.blockPos = blockPos == null ? null : blockPos.immutable();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the node id
    public String nodeId() {
        return nodeId;
    }

    // Get the kind
    public ControllerDiscoveryKind kind() {
        return kind;
    }

    // Get the group id
    public String groupId() {
        return groupId;
    }

    // Get the block id
    public String blockId() {
        return blockId;
    }

    // Get the label
    public String label() {
        return label;
    }

    // Get the sublevel id
    public @Nullable UUID subLevelId() {
        return subLevelId;
    }

    // Get the block pos
    public @Nullable BlockPos blockPos() {
        return blockPos == null ? null : blockPos.immutable();
    }

    // Check if this is valid
    public boolean isValid() {
        return !nodeId.isEmpty();
    }

    // Get the controller discovery node as direct target reference
    public ControllerDirectTargetReference asDirectTargetReference() {
        return new ControllerDirectTargetReference(nodeId, kind.id(), groupId, label, subLevelId, blockPos);
    }

    // Write the controller discovery node data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (!nodeId.isEmpty()) {
            tag.putString("NodeId", nodeId);
        }
        tag.putString("Kind", kind.id());
        if (!groupId.isEmpty()) {
            tag.putString("GroupId", groupId);
        }
        if (!blockId.isEmpty()) {
            tag.putString("BlockId", blockId);
        }
        if (!label.isEmpty()) {
            tag.putString("Label", label);
        }
        if (subLevelId != null) {
            tag.putUUID("SubLevelId", subLevelId);
        }
        if (blockPos != null) {
            tag.putLong("BlockPos", blockPos.asLong());
        }
        return tag;
    }

    // Read the controller discovery node data
    public static @Nullable ControllerDiscoveryNode fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        String nodeId = tag.getString("NodeId");
        if (nodeId.isEmpty()) {
            return null;
        }

        ControllerDiscoveryKind kind = ControllerDiscoveryKind.byId(tag.getString("Kind"));
        UUID subLevelId = tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : null;
        BlockPos blockPos = tag.contains("BlockPos") ? BlockPos.of(tag.getLong("BlockPos")) : null;
        return new ControllerDiscoveryNode(
                nodeId,
                kind == null ? ControllerDiscoveryKind.UNKNOWN : kind,
                tag.getString("GroupId"),
                tag.getString("BlockId"),
                tag.getString("Label"),
                subLevelId,
                blockPos);
    }

    // Compare this controller discovery node with another object
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ControllerDiscoveryNode node)) {
            return false;
        }
        return nodeId.equals(node.nodeId)
                && kind == node.kind
                && groupId.equals(node.groupId)
                && blockId.equals(node.blockId)
                && label.equals(node.label)
                && Objects.equals(subLevelId, node.subLevelId)
                && Objects.equals(blockPos, node.blockPos);
    }

    // Generate the controller discovery node hash
    @Override
    public int hashCode() {
        return Objects.hash(nodeId, kind, groupId, blockId, label, subLevelId, blockPos);
    }

    // Normalize the controller discovery node
    private static String normalize(String val) {
        return val == null ? "" : val.trim();
    }
}
