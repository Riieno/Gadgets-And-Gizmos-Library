package com.rieno.gadgetsandgizmos.lib.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatArgumentsTest{
    @Test
    void stripsTheStartWordAndPreservesTextArguments(){
        assertEquals(List.of("32", "-1", "test"), ChatArguments.capture("gizmos", "gizmos 32 -1 test").orElseThrow());
        assertEquals(List.of("0032", "-1.5", "Test"), ChatArguments.capture("gizmos", "gizmos 0032 -1.5 Test").orElseThrow());
    }

    @Test
    void requiresAnExactCaseSensitiveWordAtTheStart(){
        for(String message : List.of("Gizmos 32", "gizmos_more 32", "gizmos32", " gizmos 32", "other gizmos 32")){
            assertTrue(ChatArguments.capture("gizmos", message).isEmpty(), message);
        }
    }

    @Test
    void distinguishesAnEmptyArgumentListFromNoMatch(){
        assertEquals(List.of(), ChatArguments.capture("gizmos", "gizmos").orElseThrow());
        assertEquals(List.of(), ChatArguments.capture("gizmos", "gizmos   ").orElseThrow());
        assertTrue(ChatArguments.capture("gizmos", "other").isEmpty());
    }

    @Test
    void collapsesSeparatingWhitespaceWithoutEmptyTokens(){
        assertEquals(List.of("32", "-1", "test"), ChatArguments.capture("gizmos", "gizmos  32\t-1   test  ").orElseThrow());
        assertEquals(List.of("32", "test"), ChatArguments.capture("gizmos", "gizmos\u200332\u2003test").orElseThrow());
    }

    @Test
    void treatsThePrefixLiterally(){
        assertEquals(List.of("test"), ChatArguments.capture("[gizmos]", "[gizmos] test").orElseThrow());
        assertEquals(List.of("test"), ChatArguments.capture("gizmos set", "gizmos set test").orElseThrow());
    }

    @Test
    void rejectsMissingAndBlankPrefixes(){
        assertTrue(ChatArguments.capture("", "gizmos 32").isEmpty());
        assertTrue(ChatArguments.capture("   ", "gizmos 32").isEmpty());
        assertTrue(ChatArguments.capture(null, "gizmos 32").isEmpty());
        assertTrue(ChatArguments.capture("gizmos", null).isEmpty());
    }
}

