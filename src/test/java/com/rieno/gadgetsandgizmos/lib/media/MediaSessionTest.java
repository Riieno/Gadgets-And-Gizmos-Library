package com.rieno.gadgetsandgizmos.lib.media;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

class MediaSessionTest{
    private static final ResourceLocation A = ResourceLocation.parse("test:a");
    private static final ResourceLocation B = ResourceLocation.parse("test:b");
    private static final ResourceLocation C = ResourceLocation.parse("test:c");
    private static final ToIntFunction<ResourceLocation> LENGTHS = id -> Map.of(A, 100, B, 200, C, 300).getOrDefault(id, 0);

    @Test void loopsInOrderAtExactBoundaries(){
        var session = session();
        assertFalse(session.advance(99, LENGTHS));
        assertTrue(session.advance(100, LENGTHS));
        assertEquals(B, session.playback().discId());
        assertEquals(0, session.playback().elapsed(100));
        session.advance(300, LENGTHS);
        assertEquals(A, session.playback().discId());
        assertTrue(session.playback().playing());
    }

    @Test void catchesUpAcrossLongUnloadsWithoutIteratingEveryCycle(){
        var session = session();
        session.advance(300_000_150L, LENGTHS);
        assertEquals(B, session.playback().discId());
        assertEquals(50, session.playback().elapsed(300_000_150L));
    }

    @Test void pauseAndResumePreservePosition(){
        var session = session();
        session.togglePlayback(40, List.of(A, B), LENGTHS);
        assertFalse(session.playback().playing());
        assertFalse(session.advance(1000, LENGTHS));
        session.togglePlayback(1000, List.of(A, B), LENGTHS);
        assertEquals(40, session.playback().elapsed(1000));
        session.advance(1060, LENGTHS);
        assertEquals(B, session.playback().discId());
    }

    @Test void removingCurrentLastTrackWrapsAndRemovingAllStops(){
        var session = session();
        UUID id = session.selected();
        session.step(1, 0, List.of(), LENGTHS);
        session.toggle(id, B, 0, LENGTHS);
        assertEquals(A, session.playback().discId());
        session.toggle(id, A, 0, LENGTHS);
        assertFalse(session.playback().playing());
        assertNull(session.playback().discId());
        session.togglePlayback(5, List.of(A, B), LENGTHS);
        assertFalse(session.playback().playing());
    }

    @Test void saveRoundTripPreservesMembershipSelectionAndTimeline(){
        var session = session();
        session.advance(150, LENGTHS);
        var saved = session.toTag();
        var loaded = MediaSession.fromTag(saved);
        assertEquals(session.playlists(), loaded.playlists());
        assertEquals(session.selected(), loaded.selected());
        assertEquals(session.playback(), loaded.playback());
        assertEquals(saved, loaded.toTag());
    }

    @Test void removedModDiscsAreSkipped(){
        var session = session();
        session.toggle(session.selected(), C, 0, LENGTHS);
        session.advance(100, id -> id.equals(B) ? 0 : LENGTHS.applyAsInt(id));
        assertEquals(C, session.playback().discId());
    }

    @Test void standaloneDiscStopsAndPlaylistNamesAreValidated(){
        var session = session();
        session.play(C, 0, LENGTHS);
        assertNull(session.selected());
        session.advance(300, LENGTHS);
        assertFalse(session.playback().playing());
        assertThrows(IllegalArgumentException.class, () -> session.create(" favorites "));
        assertThrows(IllegalArgumentException.class, () -> session.create(" "));
        assertThrows(IllegalArgumentException.class, () -> session.rename(UUID.randomUUID(), "Other"));
    }

    @Test void singleDiscPlaylistLoopsAndNavigationWraps(){
        var session = new MediaSession();
        var id = session.create("Solo");
        session.toggle(id, A, 0, LENGTHS);
        session.select(id, 0, LENGTHS);
        session.advance(105, LENGTHS);
        assertEquals(A, session.playback().discId());
        assertEquals(5, session.playback().elapsed(105));
        session.step(-1, 105, List.of(), LENGTHS);
        assertEquals(0, session.playback().elapsed(105));
    }

    private static MediaSession session(){
        var session = new MediaSession();
        var id = session.create("Favorites");
        session.toggle(id, A, 0, LENGTHS);
        session.toggle(id, B, 0, LENGTHS);
        session.select(id, 0, LENGTHS);
        return session;
    }
}
