package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompoundTagCopiesTest{
    @Test
    void omittedGraphsAreNotCopiedAndIncludedValuesRemainIndependent(){
        CompoundTag src = new CompoundTag();
        CompoundTag graph = spy(new CompoundTag());
        CompoundTag values = new CompoundTag();
        values.putInt("speed", 4);
        src.put("Graph", graph);
        src.put("Values", values);
        CompoundTag copy = CompoundTagCopies.copyExcept(src, Set.of("Graph"));
        assertFalse(copy.contains("Graph"));
        verify(graph, never()).copy();
        copy.getCompound("Values").putInt("speed", 10);
        assertEquals(4, values.getInt("speed"));
        assertEquals(10, copy.getCompound("Values").getInt("speed"));
    }
}
