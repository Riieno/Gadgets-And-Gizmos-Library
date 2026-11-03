package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompoundTagTraversalTest{
    @Test
    void postOrderAllowsInPlaceMigrationWithoutCopyingKeySets(){
        CompoundTag root = new CompoundTag();
        CompoundTag child = new CompoundTag();
        child.putString("id", "child");
        child.putInt("legacy", 1);
        ListTag items = new ListTag();
        items.add(child);
        root.put("Items", items);
        root.putString("id", "root");
        var order = new ArrayList<String>();
        int changed = CompoundTagTraversal.visitPostOrder(root, tag -> {
            order.add(tag.getString("id"));
            tag.remove("legacy");
            tag.putBoolean("Migrated", true);
            return true;
        });
        assertEquals(List.of("child", "root"), order);
        assertEquals(2, changed);
        assertFalse(child.contains("legacy"));
        assertTrue(child.getBoolean("Migrated"));
    }

    @Test
    void schemaPruningIsLimitedToTheSpecifiedParent(){
        CompoundTag root = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putString("id", "palette");
        root.put("sections", palette);
        CompoundTag inventory = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("id", "item");
        inventory.put("sections", nested);
        root.put("Inventory", inventory);
        ListTag numbers = new ListTag();
        for(int idx = 0; idx < 1000; idx++) numbers.add(IntTag.valueOf(idx));
        root.put("Numbers", numbers);
        var visited = new ArrayList<CompoundTag>();
        CompoundTagTraversal.visitPostOrder(root, tag -> { visited.add(tag); return false; },
                (parent, key) -> parent != root || !"sections".equals(key));
        assertFalse(visited.contains(palette));
        assertTrue(visited.contains(nested));
        assertEquals(3, visited.size());
    }
}
