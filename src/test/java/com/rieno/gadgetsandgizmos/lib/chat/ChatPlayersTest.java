package com.rieno.gadgetsandgizmos.lib.chat;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatPlayersTest{
    @Test
    void acceptsRawAndTypedStringListEntries(){
        assertEquals(List.of("Alice", "Bob"), ChatPlayers.identifiers(
                GraphValue.list(List.of("Alice", GraphValue.string("Bob")))));
    }

    @Test
    void invalidEntriesDoNotBecomeAnEmptyList(){
        assertEquals(List.of("", ""), ChatPlayers.identifiers(
                GraphValue.list(List.of(42, GraphValue.bool(true)))));
        assertEquals(List.of(), ChatPlayers.identifiers(GraphValue.string("Alice")));
        assertEquals(List.of(), ChatPlayers.identifiers(null));
    }

    @Test
    void parsesNamesAndUuidsWithoutChangingCase(){
        String id = "abcdef12-3456-7890-abcd-123456789abc";
        assertEquals(List.of("Alice", "Bob", id), ChatPlayers.parse(" Alice, Bob\t" + id + "  "));
        assertEquals(List.of("Alice", "Bob"), ChatPlayers.parse("Alice\u2003Bob"));
    }

    @Test
    void emptyTextProducesAnEmptyList(){
        assertEquals(List.of(), ChatPlayers.parse(null));
        assertEquals(List.of(), ChatPlayers.parse(""));
        assertEquals(List.of(), ChatPlayers.parse("  , , "));
    }

    @Test
    void formatsEditableListsAndRoundTripsThem(){
        List<String> names = List.of("Alice", "Bob");
        assertEquals("Alice, Bob", ChatPlayers.format(names));
        assertEquals(names, ChatPlayers.parse(ChatPlayers.format(names)));
        assertEquals("", ChatPlayers.format(Arrays.asList(null, "")));
    }
}
