package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphNodeLayoutRowsTest{
    @Test void orderedRowsHaveSeparateOffsetsAndAnExtentForNodeSizing(){
        var rows = GraphNodeLayout.portRows(List.of("exec","failure","done","cancelled"),13);
        assertEquals(List.of("exec","failure","done","cancelled"),List.copyOf(rows.offsets().keySet()));
        assertEquals(List.of(0,13,26,39),List.copyOf(rows.offsets().values()));
        assertEquals(52,rows.height());
        assertThrows(UnsupportedOperationException.class,() -> rows.offsets().put("extra",52));
    }

    @Test void emptyInvalidAndDuplicateIdsDoNotCreatePhantomRows(){
        assertEquals(0,GraphNodeLayout.portRows(null,13).height());
        var rows = GraphNodeLayout.portRows(Arrays.asList(null,""," ","start","start","stop"),0);
        assertEquals(List.of(0,1),List.copyOf(rows.offsets().values()));assertEquals(2,rows.height());
    }
}
