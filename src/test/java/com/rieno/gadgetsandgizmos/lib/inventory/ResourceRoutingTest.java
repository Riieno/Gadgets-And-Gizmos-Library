package com.rieno.gadgetsandgizmos.lib.inventory;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRoutingTest{
    @Test
    void emptyAndSingletonPoolsDoNotCreateAdditionalStorage(){
        assertSame(ResourceRouting.Pool.EMPTY, ResourceRouting.combine(List.of()));
        assertSame(ResourceRouting.Pool.EMPTY, ResourceRouting.combine(List.of(ResourceRouting.Pool.EMPTY)));
        assertEquals(List.of(), ResourceRouting.distinct(List.of()));
        assertEquals(List.of(), ResourceRouting.distinct(Arrays.asList((Object)null)));
        assertThrows(NullPointerException.class, () -> ResourceRouting.combine(Arrays.asList((ResourceRouting.Pool)null)));
    }

    @Test
    void deduplicationUsesIdentityAndKeepsTheOriginalOrder(){
        String first = new String("same");
        String second = new String("same");
        var res = ResourceRouting.distinct(Arrays.asList(null, first, first, second, null, first));
        assertEquals(2, res.size());
        assertSame(first, res.get(0));
        assertSame(second, res.get(1));
        assertSame(first, ResourceRouting.distinct(List.of(first)).getFirst());
        assertThrows(UnsupportedOperationException.class, () -> res.add("other"));
    }
}
