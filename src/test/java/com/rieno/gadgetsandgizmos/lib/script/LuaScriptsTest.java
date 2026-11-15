package com.rieno.gadgetsandgizmos.lib.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LuaScriptsTest{
    @Test void portNamesCannotInjectLuaSource(){
        assertEquals("ports.speed", LuaScripts.access("ports", "speed"));
        assertEquals("output[\"end\"]", LuaScripts.access("output", "end"));
        assertEquals("ports[\"a\\\"]); error(\\\"bad\\\") --\"]", LuaScripts.access("ports", "a\"]); error(\"bad\") --"));
        assertEquals("ports[\"a\\nb\"]", LuaScripts.access("ports", "a\nb"));
        assertEquals("local ports, output = ...;\noutput.result = ports.value", LuaScripts.source("output.result = ports.value"));
    }

    @Test void multilineStringsAndCommentsHideKeywords(){
        String src = "--[=[ local\nreturn ]=]\nlocal text = [==[if\nend]==]\nreturn 12e-3";
        var spans = LuaSyntax.spans(src);
        assertEquals(1, spans.stream().filter(span -> span.kind() == LuaSyntax.Kind.COMMENT).count());
        assertEquals(1, spans.stream().filter(span -> span.kind() == LuaSyntax.Kind.STRING).count());
        assertEquals(2, spans.stream().filter(span -> span.kind() == LuaSyntax.Kind.KEYWORD).count());
        assertEquals("12e-3", spans.stream().filter(span -> span.kind() == LuaSyntax.Kind.NUMBER)
                .map(span -> src.substring(span.start(), span.end())).findFirst().orElseThrow());
    }

    @Test void unterminatedStringsAndCommentsRemainEditable(){
        assertEquals(LuaSyntax.Kind.STRING, LuaSyntax.spans("[=[unfinished\nlocal").getFirst().kind());
        assertEquals(LuaSyntax.Kind.COMMENT, LuaSyntax.spans("--[[unfinished\nreturn").getFirst().kind());
        assertEquals(LuaSyntax.Kind.STRING, LuaSyntax.spans("\"escaped \\\" if\"").getFirst().kind());
    }
}
