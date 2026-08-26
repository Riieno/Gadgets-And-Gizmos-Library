package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Locale;
import java.util.Set;

// Keep graph data access from creating or replacing stored item contents
public final class BlockEntityDataAccessPolicy {
    private static final Set<String> ITEM_CONTENT_PORTS = Set.of(
            "consumed_item",
            "contents",
            "controller",
            "filter",
            "held_item",
            "held_stack",
            "inventory",
            "inventory_contents",
            "item",
            "item_amount",
            "item_count",
            "item_stack",
            "items",
            "scripted_result",
            "stack",
            "stack_count",
            "stack_in_slot",
            "stack_size",
            "stack_to_distribute"
    );
    private static final Set<String> ITEM_CONTENT_TYPES = Set.of(
            "inventory",
            "item",
            "item_stack",
            "items",
            "stack"
    );
    private static final Set<String> COMPACT_ITEM_CONTENT_PORTS = Set.of(
            "consumeditem",
            "contents",
            "controller",
            "filter",
            "helditem",
            "heldstack",
            "inventory",
            "inventorycontents",
            "item",
            "itemamount",
            "itemcount",
            "items",
            "itemstack",
            "scriptedresult",
            "stack",
            "stackcount",
            "stackinslot",
            "stacksize",
            "stacktodistribute"
    );

    // Initialize the block entity data access policy
    private BlockEntityDataAccessPolicy() {
    }

    // Check if one port can replace item contents without consuming an item
    public static boolean isItemContentMutation(String port) {
        if (port == null || port.isBlank()) {
            return false;
        }
        String normalized = port.strip().toLowerCase(Locale.ROOT);
        String candidate = normalized.startsWith("call_")
                ? normalized.substring("call_".length()) : normalized;
        String compact = candidate.replaceAll("[^a-z0-9]", "");
        return ITEM_CONTENT_PORTS.contains(candidate)
                || COMPACT_ITEM_CONTENT_PORTS.contains(compact)
                || candidate.endsWith("_item")
                || candidate.endsWith("_item_stack")
                || candidate.endsWith("_held_stack")
                || compact.endsWith("itemstack")
                || compact.endsWith("itemcount")
                || compact.endsWith("itemamount")
                || compact.endsWith("helditem")
                || compact.endsWith("heldstack")
                || compact.endsWith("stack")
                || compact.endsWith("stackcount")
                || compact.endsWith("stacksize")
                || compact.endsWith("contents")
                || compact.endsWith("filter")
                || candidate.startsWith("item_slot_")
                || candidate.startsWith("inventory_slot_")
                || candidate.startsWith("stack_in_slot_")
                || candidate.startsWith("slot_item_")
                || compact.startsWith("itemslot")
                || compact.startsWith("inventoryslot")
                || compact.startsWith("stackinslot")
                || compact.startsWith("slotitem")
                || compact.contains("itemslot")
                || compact.contains("inventoryslot")
                || compact.contains("stackinslot")
                || compact.contains("slotitem");
    }

    // Check if one typed port can replace item contents without consuming an item
    public static boolean isItemContentMutation(String port, String type) {
        String normalizedType = type == null ? "" : type.strip().toLowerCase(Locale.ROOT);
        String compactType = normalizedType.replaceAll("[^a-z0-9]", "");
        return isItemContentMutation(port) || ITEM_CONTENT_TYPES.contains(normalizedType)
                || normalizedType.endsWith("_item")
                || normalizedType.endsWith("_item_stack")
                || compactType.equals("itemstack")
                || compactType.equals("item")
                || compactType.equals("inventory")
                || compactType.endsWith("itemstack");
    }
}
