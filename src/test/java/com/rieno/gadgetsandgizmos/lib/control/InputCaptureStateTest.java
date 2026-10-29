package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class InputCaptureStateTest{
    @Test void sharedInputRemainsCapturedUntilItsLastSourceReleases(){
        var state = new InputCaptureState();
        assertTrue(state.replace(Map.of("first", "mouse_x", "second", "mouse_x", "button", "left_click")));
        assertEquals(Set.of("mouse_x", "left_click"), state.inputs());
        state.replace(Map.of("second", "mouse_x"));
        assertTrue(state.captures("mouse_x"));
        assertFalse(state.requestedBy("first"));
        assertFalse(state.captures("left_click"));
        state.replace(Map.of());
        assertTrue(state.inputs().isEmpty());
    }

    @Test void snapshotsAreDetachedAndUnchangedRequestsDoNotPublishAgain(){
        var requests = new HashMap<>(Map.of("node", "mouse_y"));
        var state = new InputCaptureState();
        assertTrue(state.replace(requests));
        requests.clear();
        assertTrue(state.captures("mouse_y"));
        assertFalse(state.replace(Map.of("node", "mouse_y")));
        assertThrows(UnsupportedOperationException.class, () -> state.requests().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.inputs().clear());
    }

    @Test void completeSerializedSnapshotsAlsoReleasePreviousRequests(){
        var source = new InputCaptureState();
        source.replace(Map.of("node", "scroll_up"));
        var client = new InputCaptureState();
        assertTrue(client.read(source.write()));
        assertEquals(source.requests(), client.requests());
        assertTrue(client.read(new CompoundTag()));
        assertTrue(client.inputs().isEmpty());
        assertFalse(client.read(new CompoundTag()));
    }
}
