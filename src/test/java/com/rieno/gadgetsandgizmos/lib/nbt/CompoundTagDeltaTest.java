package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompoundTagDeltaTest{
    @Test
    void graphEditsAndRemovalsRoundTripWithoutRepeatingUnchangedNodes(){
        CompoundTag prev = graph();
        CompoundTag next = prev.copy();
        next.getList("Nodes", 10).getCompound(0).putInt("Value", 42);
        next.remove("Removed");
        CompoundTag patch = CompoundTagDelta.between(prev, next);
        assertFalse(patch.toString().contains("unchanged payload"));
        CompoundTag restored = prev.copy();
        CompoundTagDelta.apply(restored, patch);
        assertEquals(next, restored);
        CompoundTagDelta.apply(restored, CompoundTagDelta.between(next, prev));
        assertEquals(prev, restored);
        next.getList("Nodes", 10).getCompound(0).putInt("Value", 99);
        CompoundTagDelta.apply(restored, patch);
        assertEquals(42, restored.getList("Nodes", 10).getCompound(0).getInt("Value"));
    }

    @Test
    void addedNodesAndChangedTagTypesRoundTrip(){
        CompoundTag prev = graph();
        CompoundTag next = prev.copy();
        next.getList("Nodes", 10).add(new CompoundTag());
        next.putString("Removed", "replacement");
        CompoundTagDelta.apply(prev, CompoundTagDelta.between(prev, next));
        assertEquals(next, prev);
        assertTrue(CompoundTagDelta.between(next, next).isEmpty());
    }

    @Test
    void insertingAndRemovingNodesPreservesUnchangedSuffixWithoutCopyingIt(){
        CompoundTag prev = graph();
        CompoundTag next = prev.copy();
        next.getList("Nodes", 10).add(1, new CompoundTag());
        CompoundTag patch = CompoundTagDelta.between(prev, next);
        assertFalse(patch.toString().contains("unchanged payload"));
        CompoundTagDelta.apply(prev, patch);
        assertEquals(next, prev);
        next.getList("Nodes", 10).remove(1);
        CompoundTagDelta.apply(prev, CompoundTagDelta.between(prev, next));
        assertEquals(next, prev);
    }

    private static CompoundTag graph(){
        CompoundTag root = new CompoundTag();
        ListTag nodes = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putInt("Value", 1);
        nodes.add(first);
        CompoundTag second = new CompoundTag();
        second.putString("Data", "unchanged payload");
        nodes.add(second);
        root.put("Nodes", nodes);
        root.putBoolean("Removed", true);
        return root;
    }
}
