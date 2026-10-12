package com.rieno.gadgetsandgizmos.lib.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OnlinePlayerSelectionTest{
    private static final OnlinePlayerSelection.Player ALICE = new OnlinePlayerSelection.Player(new UUID(1,2), "Alice");
    private static final OnlinePlayerSelection.Player BOB = new OnlinePlayerSelection.Player(new UUID(3,4), "Bob");

    @Test
    void emptySelectionDoesNotCheckEveryone(){
        assertFalse(new OnlinePlayerSelection(List.of()).contains(ALICE));
    }
    @Test
    void recognizesSavedNamesAndUuidAliases(){
        var selected = new OnlinePlayerSelection(List.of("alice", BOB.id().toString()));
        assertTrue(selected.contains(ALICE));assertTrue(selected.contains(BOB));
    }
    @Test
    void togglingRemovesEveryAliasThenStoresTheUuid(){
        var selected = new OnlinePlayerSelection(List.of("Alice", ALICE.id().toString()));
        selected.toggle(ALICE);assertTrue(selected.identifiers().isEmpty());
        selected.toggle(ALICE);assertEquals(List.of(ALICE.id().toString()), selected.identifiers());
    }
    @Test
    void preservesOfflineRecipientsWhenSelectingOnlinePlayers(){
        var selected = new OnlinePlayerSelection(List.of("Offline"));
        selected.selectAll(List.of(ALICE,BOB));
        assertEquals(List.of("Offline",ALICE.id().toString(),BOB.id().toString()),selected.identifiers());
        assertEquals(1, selected.offlineCount(List.of(ALICE,BOB)));
        selected.clear();assertTrue(selected.identifiers().isEmpty());
    }
    @Test
    void selectAllDoesNotDuplicateAnExistingNameSelection(){
        var selected = new OnlinePlayerSelection(List.of("Alice"));
        selected.selectAll(List.of(ALICE,ALICE));assertEquals(List.of("Alice"),selected.identifiers());
    }
    @Test
    void aDisconnectDoesNotDropTheSavedSelection(){
        var selected = new OnlinePlayerSelection(List.of(ALICE.id().toString()));
        assertEquals(1,selected.offlineCount(List.of(BOB)));assertTrue(selected.contains(ALICE));
    }
}
