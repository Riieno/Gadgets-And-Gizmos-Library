package com.rieno.gadgetsandgizmos.lib.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Check attachment restoration timing across nested and failed transfers
class SubLevelTransferScopeTest{
    // Wait for the outer body and all source removals before restoring either attachment
    @Test
    void nestedTransfersCompleteAfterTheOuterBodyIsReady(){
        List<String> phases = new ArrayList<>();
        String res = SubLevelTransferScope.run(() -> {
            SubLevelTransferScope.afterTransfer(() -> phases.add("first attachment"));
            SubLevelTransferScope.run(() -> {
                SubLevelTransferScope.afterTransfer(() -> phases.add("second attachment"));
                return null;
            });
            assertTrue(phases.isEmpty());
            phases.add("body ready");
            return "assembled";
        });
        assertEquals("assembled", res);
        assertEquals(List.of("body ready", "first attachment", "second attachment"), phases);
    }

    // Discard failed transfers without leaving callbacks attached to the next operation
    @Test
    void failedTransfersDiscardCallbacksAndReleaseTheirScope(){
        List<String> phases = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> SubLevelTransferScope.run(() -> {
            SubLevelTransferScope.afterTransfer(() -> phases.add("failed attachment"));
            throw new IllegalStateException("assembly failed");
        }));
        SubLevelTransferScope.afterTransfer(() -> phases.add("available attachment"));
        assertEquals(List.of("available attachment"), phases);
    }
}
