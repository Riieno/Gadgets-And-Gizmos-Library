package com.rieno.gadgetsandgizmos.lib.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldRenderControlTest{
    @Test void onlyAnOptedInCoveringScreenSkipsTheMainWorld(){
        assertTrue(WorldRenderControl.shouldRenderWorld(true, null));
        assertTrue(WorldRenderControl.shouldRenderWorld(true, new Object()));
        assertTrue(WorldRenderControl.shouldRenderWorld(true, (WorldRenderControl)() -> false));
        assertFalse(WorldRenderControl.shouldRenderWorld(true, (WorldRenderControl)() -> true));
        assertFalse(WorldRenderControl.shouldRenderWorld(false, (WorldRenderControl)() -> { throw new AssertionError("Policy should not run"); }));
    }
}
