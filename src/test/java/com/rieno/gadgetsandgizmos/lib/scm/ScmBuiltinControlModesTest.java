package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmBuiltinControlModesTest {
    @Test
    void gatesExperimentalIkRegistration(){
        ScmBuiltinControlModes.setIkEnabled(false);
        assertFalse(ScmControlModeRegistry.serializedIds().contains("ik"));
        assertEquals(ScmBuiltinControlModes.AIRSHIP_ID,
                ScmControlModeRegistry.resolve("ik").id());

        ScmBuiltinControlModes.setIkEnabled(true);
        assertTrue(ScmControlModeRegistry.serializedIds().contains("ik"));

        ScmBuiltinControlModes.setIkEnabled(false);
    }
}
