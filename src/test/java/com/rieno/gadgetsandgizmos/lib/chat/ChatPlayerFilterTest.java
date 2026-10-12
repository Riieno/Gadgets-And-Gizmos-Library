package com.rieno.gadgetsandgizmos.lib.chat;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChatPlayerFilterTest{
    private static final UUID PLAYER = UUID.fromString("abcdefff-1234-5678-9abc-123456789abc");

    @Test
    void emptyListsAcceptEveryone(){
        assertTrue(ChatPlayerFilter.matches(List.of(), PLAYER, "PlayerOne"));
        assertTrue(ChatPlayerFilter.matches(null, PLAYER, "PlayerOne"));
    }

    @Test
    void namesRequireTheWholeAccountName(){
        assertTrue(ChatPlayerFilter.matches(List.of("someone", "playerone"), PLAYER, "PlayerOne"));
        assertTrue(ChatPlayerFilter.matches(List.of(" PlayerOne "), PLAYER, "PlayerOne"));
        assertFalse(ChatPlayerFilter.matches(List.of("Player"), PLAYER, "PlayerOne"));
        assertFalse(ChatPlayerFilter.matches(List.of("PlayerOneExtra"), PLAYER, "PlayerOne"));
    }

    @Test
    void uuidEntriesMatchTheAuthenticatedSender(){
        assertTrue(ChatPlayerFilter.matches(List.of(PLAYER.toString().toUpperCase()), PLAYER, "renamed"));
        assertFalse(ChatPlayerFilter.matches(List.of(UUID.randomUUID().toString()), PLAYER, "PlayerOne"));
    }

    @Test
    void unknownAndBlankEntriesDoNotOpenTheFilter(){
        assertFalse(ChatPlayerFilter.matches(Arrays.asList(null, "", "  "), PLAYER, "PlayerOne"));
        assertFalse(ChatPlayerFilter.matches(List.of("someone"), PLAYER, "PlayerOne"));
    }

    @Test
    void missingIdentityCannotMatch(){
        assertFalse(ChatPlayerFilter.matches(List.of("PlayerOne", PLAYER.toString()), null, null));
    }
}
