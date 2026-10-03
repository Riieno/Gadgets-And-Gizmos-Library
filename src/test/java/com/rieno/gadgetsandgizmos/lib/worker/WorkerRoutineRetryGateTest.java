package com.rieno.gadgetsandgizmos.lib.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRoutineRetryGateTest{
    @Test void retriesOnlyAfterTheDelayAndResetsWhenTheGraphChanges(){
        WorkerRoutineRetryGate gate = new WorkerRoutineRetryGate();
        assertTrue(gate.ready("craft", 100L));
        gate.defer("craft", 100L, 200L);
        assertFalse(gate.ready("craft", 299L));
        assertTrue(gate.ready("craft", 300L));
        assertTrue(gate.ready("other", 101L));
        gate.clear("craft");
        assertTrue(gate.ready("craft", 101L));
        gate.defer("craft", 200L, 200L);
        gate.clear();
        assertTrue(gate.ready("craft", 201L));
    }
}
