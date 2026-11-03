package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GraphInteractionOutputsTest{
    @Test
    void serializationVisitorRetainsValuesAndTheirInteractionOwners(){
        var outputs = new GraphInteractionOutputs<Integer>();
        UUID actor = UUID.randomUUID();
        outputs.submit("first", 1, actor);
        outputs.submit("second", 2, null);
        var keys = new ArrayList<String>();
        outputs.visit((key, submission) -> {
            keys.add(key);
            assertEquals(outputs.submission(key), submission);
        });
        assertEquals(java.util.List.of("first", "second"), keys);
        assertEquals(actor, outputs.submission("first").playerId());
        assertNull(outputs.submission("second").playerId());
    }
}
