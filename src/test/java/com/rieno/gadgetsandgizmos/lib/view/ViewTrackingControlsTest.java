package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlLayout;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewTrackingControlsTest{
    @Test void olderStateAndCommandsDoNotEnableOrResetTracking(){
        assertFalse(ViewControlState.fromTag(new CompoundTag()).autoTracking());
        assertFalse(new ViewControlState(ViewRig.Mode.SENTRY, 70, false).autoTracking());
        ViewControlState tracking = new ViewControlState(ViewRig.Mode.SENTRY, 40, true, true);
        var oldCommand = new ViewControlInput(0, 0, -1, "", -1);
        assertTrue(oldCommand.settings(tracking).autoTracking());
        assertEquals(-1, ViewControlInput.fromTag(new CompoundTag()).autoTracking());
    }

    @Test void roundTripsTrackingAndRejectsInvalidCommands(){
        ViewControlState state = new ViewControlState(ViewRig.Mode.SENTRY, 40, true, true);
        assertEquals(state, ViewControlState.fromTag(state.toTag()));
        var disable = new ViewControlInput(0, 0, -1, "", -1, 0);
        assertTrue(disable.valid());
        assertFalse(ViewControlInput.fromTag(disable.toTag()).settings(state).autoTracking());
        assertEquals(state.fov(), disable.settings(state).fov());
        assertTrue(disable.settings(state).flashlight());
        assertFalse(new ViewControlInput(0, 0, -1, "", -1, 2).valid());
        assertFalse(new ViewControlInput(0, 0, -1, "", -1, -2).valid());
    }

    @Test void trackingHasASeparateHitRegionAtNormalDisplaySizes(){
        for(int[] size : new int[][]{{264, 168}, {320, 180}, {800, 450}, {1920, 1080}}){
            ViewControlLayout layout = ViewControlLayout.overlay(17, 23, size[0], size[1]);
            var tracking = layout.tracking();
            assertTrue(tracking.contains(tracking.centerX(), tracking.centerY()));
            assertFalse(layout.flashlight().contains(tracking.centerX(), tracking.centerY()));
            assertFalse(layout.modes().stream().anyMatch(mode -> mode.contains(tracking.centerX(), tracking.centerY())));
            assertTrue(Math.hypot(tracking.centerX() - layout.stickX(), tracking.centerY() - layout.stickY()) > layout.radius() + 5);
        }
    }

    @Test void lockCommandsRoundTripWithoutResettingOtherSettings(){
        ViewControlState state = new ViewControlState(ViewRig.Mode.SENTRY, 40, true, true, true);
        assertEquals(state, ViewControlState.fromTag(state.toTag()));
        assertFalse(ViewControlState.fromTag(new CompoundTag()).lockTarget());
        assertFalse(new ViewControlState(ViewRig.Mode.SENTRY, 40, true, true).lockTarget());
        assertEquals(-1, ViewControlInput.fromTag(new CompoundTag()).lockTarget());
        assertTrue(new ViewControlInput(0, 0, -1, "", -1, 1).settings(state).lockTarget());
        var unlock = new ViewControlInput(0, 0, -1, "", -1, -1, 0);
        assertTrue(unlock.valid());
        assertEquals(unlock, ViewControlInput.fromTag(unlock.toTag()));
        assertEquals(new ViewControlState(ViewRig.Mode.SENTRY, 40, true, true, false), unlock.settings(state));
        assertFalse(new ViewControlInput(0, 0, -1, "", -1, -1, 2).valid());
        assertFalse(new ViewControlInput(0, 0, -1, "", -1, -1, -2).valid());
    }

    @Test void lockIconsHaveSeparatePointerRegionsOnBothControlSurfaces(){
        for(ViewControlLayout layout : new ViewControlLayout[]{ViewControlLayout.compact(17, 23, 320, 180),
                ViewControlLayout.overlay(17, 23, 264, 168), ViewControlLayout.overlay(17, 23, 1920, 1080)}){
            var lock = layout.targetLock();
            assertTrue(lock.contains(lock.centerX(), lock.centerY()));
            assertFalse(layout.tracking().contains(lock.centerX(), lock.centerY()));
            assertFalse(layout.flashlight().contains(lock.centerX(), lock.centerY()));
            assertFalse(layout.zoom().contains(lock.centerX(), lock.centerY()));
            assertFalse(layout.modes().stream().anyMatch(mode -> mode.contains(lock.centerX(), lock.centerY())));
            assertTrue(Math.hypot(lock.centerX() - layout.stickX(), lock.centerY() - layout.stickY()) > layout.radius() + 5);
        }
    }
}
