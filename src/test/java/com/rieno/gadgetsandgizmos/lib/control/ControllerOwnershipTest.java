package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerOwnershipTest{
    @Test
    void ownershipRoundTripsWithoutLettingAnotherChannelRelabelOrReleaseIt(){
        var owner = ControllerOwnership.NONE.claim("controller_a", "test.owner");
        assertEquals(owner, ControllerOwnership.read(owner.save()));
        assertEquals(owner, owner.claim("controller_b", "other.owner"));
        assertEquals(owner, owner.release("controller_b"));
        assertEquals(ControllerOwnership.NONE, owner.release("controller_a"));
        assertFalse(ControllerOwnership.read(ControllerOwnership.NONE.save()).present());
        assertFalse(ControllerOwnership.NONE.claim("", "test.owner").present());
    }
}
