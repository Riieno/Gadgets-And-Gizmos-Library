package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphBlockPositionTest{
    @Test
    void optionalAndMalformedCoordinatesNeverSelectTheOrigin(){
        assertNull(GraphBlockPosition.read(GraphBlockPosition.value(null)));
        assertNull(GraphBlockPosition.read(GraphValue.map(Map.of("x", 2, "y", 3))));
        assertNull(GraphBlockPosition.read(GraphValue.map(Map.of("x", "invalid", "y", 3, "z", 4))));
        assertNull(GraphBlockPosition.read(GraphValue.map(Map.of("x", 3E20, "y", 3, "z", 4))));
        BlockPos pos = new BlockPos(20481032, -17, 20481032);
        assertEquals(pos, GraphBlockPosition.read(GraphBlockPosition.value(pos)));
    }
}
