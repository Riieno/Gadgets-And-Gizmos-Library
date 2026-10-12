package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphDataOperationsTest{
    @Test
    void countsStringCharactersIncludingSpaces(){
        assertEquals(12, GraphDataOperations.length(GraphValue.string("Hello World!")));
        assertEquals(0, GraphDataOperations.length(GraphValue.string("")));
    }

    @Test
    void countsTopLevelListAndMapEntries(){
        assertEquals(3, GraphDataOperations.length(GraphValue.list(List.of("Hello", List.of(1, 2), "World!"))));
        assertEquals(2, GraphDataOperations.length(GraphValue.map(Map.of("a", List.of(1, 2), "b", "text"))));
        assertEquals(0, GraphDataOperations.length(GraphValue.list(List.of())));
        assertEquals(0, GraphDataOperations.length(GraphValue.map(Map.of())));
    }

    @Test
    void handlesAnyValuesAndUnsupportedInputs(){
        assertEquals(5, GraphDataOperations.length(new GraphValue("any", "Hello")));
        assertEquals(0, GraphDataOperations.length(null));
        assertEquals(0, GraphDataOperations.length(new GraphValue("any", null)));
        assertEquals(0, GraphDataOperations.length(GraphValue.number(42)));
        assertEquals(0, GraphDataOperations.length(GraphValue.bool(true)));
    }

    @Test
    void decodesQuotedTextAndEscapes(){
        assertEquals("Hello \"World\"!\nC:\\test", GraphDataOperations.stripJson("\"Hello \\\"World\\\"!\\nC:\\\\test\""));
        assertEquals("Hello", GraphDataOperations.stripJson("\"\\u0048ello\""));
    }

    @Test
    void joinsNestedArraysAndMapValuesWithoutKeys(){
        assertEquals("Hello World!", GraphDataOperations.stripJson("[\"Hello\",[\"World!\"]]"));
        assertEquals("Hello World!", GraphDataOperations.stripJson("{\"greeting\":\"Hello\",\"nested\":{\"word\":\"World!\"}}"));
        assertEquals("32 -1 true", GraphDataOperations.stripJson("[32,-1,true,null,\"\"]"));
    }

    @Test
    void readsNumberedObjectsInNumericOrder(){
        assertEquals("Hello World!", GraphDataOperations.stripJson("{\"1\":\"World!\",\"0\":\"Hello\"}"));
        assertEquals("first second last", GraphDataOperations.stripJson("{10:\"last\",2:\"second\",0:\"first\"}"));
    }

    @Test
    void acceptsTheLooseNumberedExample(){
        assertEquals("Hello World!", GraphDataOperations.stripJson("{0\"Hello\",1:\"World!\"}"));
        assertEquals("Hello World!", GraphDataOperations.stripJson("{ 0 \"Hello\", 1 \"World!\" }"));
    }

    @Test
    void leavesPlainTextAndInvalidEncodingIntact(){
        for(String text : List.of("Hello World!", "hello", "  hello  ", " \t ", "{broken", "[\"Hello\",", "{0\"Hello\",1:}")){
            assertEquals(text, GraphDataOperations.stripJson(text));
        }
    }

    @Test
    void preservesPunctuationInsideDecodedStrings(){
        assertEquals("{0\"Hello\",1:\"World!\"}", GraphDataOperations.stripJson("\"{0\\\"Hello\\\",1:\\\"World!\\\"}\""));
        assertEquals("Hello, {World!}", GraphDataOperations.stripJson("[\"Hello,\",\"{World!}\"]"));
    }

    @Test
    void readsSerializedGraphValuesWithoutTheirTypeMetadata(){
        assertEquals("Hello World!", GraphDataOperations.stripJson(
                "{0:{Type:\"string\",Payload:{Value:\"Hello\"}},1:{Payload:{Value:\"World!\"},Type:\"string\"}}"));
        assertEquals("Hello World!", GraphDataOperations.stripJson(
                "{\"Type\":\"list\",\"Payload\":{\"0\":{\"Type\":\"string\",\"Payload\":{\"Value\":\"Hello\"}},\"1\":\"World!\"}}"));
    }

    @Test
    void handlesEmptyAndNullData(){
        for(String text : List.of("", "[]", "{}", "null", "\"\"", "[null,\"\"]")){
            assertEquals("", GraphDataOperations.stripJson(text));
        }
        assertEquals("", GraphDataOperations.stripJson(null));
    }
}
