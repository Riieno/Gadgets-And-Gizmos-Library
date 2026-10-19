package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiverSignalScopeTest{
    @Test void nestedSourceQueriesRestoreReceiverSignals(){
        assertTrue(ReceiverSignalScope.allowsReceiverSignals());
        assertEquals(7, ReceiverSignalScope.sampleSources(() -> {
            assertFalse(ReceiverSignalScope.allowsReceiverSignals());
            int result = ReceiverSignalScope.sampleSources(() -> {
                assertFalse(ReceiverSignalScope.allowsReceiverSignals());return 7;
            });
            assertFalse(ReceiverSignalScope.allowsReceiverSignals());return result;
        }));
        assertTrue(ReceiverSignalScope.allowsReceiverSignals());
    }

    @Test void failedSourceQueriesCannotSuppressLaterReceiverQueries(){
        assertThrows(IllegalStateException.class, () -> ReceiverSignalScope.sampleSources(() -> {
            throw new IllegalStateException("test");
        }));
        assertTrue(ReceiverSignalScope.allowsReceiverSignals());
    }
}
