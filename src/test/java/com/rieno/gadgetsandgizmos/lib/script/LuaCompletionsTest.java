package com.rieno.gadgetsandgizmos.lib.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LuaCompletionsTest{
    private final ScriptPeripheral thruster = new ScriptPeripheral("acc_owner/thruster_0", "thruster", List.of("thruster"),
            List.of("setThrottle", "getThrottle", "setEnabled"), List.of("linked-target"));

    private LuaCompletions.Suggestions suggest(String text){ return LuaCompletions.suggest(text, text.length(), List.of("print("), List.of(thruster), false); }

    @Test void quotedTargetNamesCompleteWithoutDuplicatingTheClosingQuote(){
        String source = "local engine = peripheral.wrap(\"thr\")";
        var res = LuaCompletions.suggest(source, source.indexOf("thr") + 3, List.of(), List.of(thruster), false);
        assertEquals(List.of("\"thruster\""), res.values());
        assertEquals("local engine = peripheral.wrap(\"thruster\")", source.substring(0, res.start()) + res.values().getFirst() + source.substring(res.end()));
        assertEquals(List.of("\"thruster\""), suggest("peripheral.call('thr").values());
    }

    @Test void wrappedVariablesCompleteTheirActualMethodsAndRespectReassignment(){
        String prefix = "local thruster = peripheral.wrap(\"thruster\")\n";
        assertEquals(List.of("thruster.setThrottle("), suggest(prefix + "thruster.setTh").values());
        assertEquals(List.of("thruster.getThrottle(", "thruster.setThrottle("), suggest(prefix + "thruster.thr").values());
        assertEquals(List.of("engine.setThrottle("), suggest(prefix + "local engine = thruster\nengine.setTh").values());
        assertTrue(suggest(prefix + "thruster = nil\nthruster.setTh").values().isEmpty());
        assertEquals(List.of("\"setThrottle\""), suggest("peripheral.call('thruster', 'setTh").values());
    }

    @Test void commentsAndStringsDoNotCreateBindingsOrUnrelatedSuggestions(){
        assertTrue(suggest("-- local thruster = peripheral.wrap('thruster')\nthruster.setTh").values().isEmpty());
        assertTrue(suggest("local text = [[thruster = peripheral.wrap('thruster')]]\nthruster.setTh").values().isEmpty());
        assertTrue(suggest("-- pri").values().isEmpty());
        assertTrue(suggest("print('pri").values().isEmpty());
    }

    @Test void insertedWrapsEscapeNamesAndAvoidVariableCollisions(){
        assertEquals("local thruster2 = peripheral.wrap(\"thruster\")\n", LuaCompletions.wrapSnippet(thruster, "local thruster = {}"));
        var named = new ScriptPeripheral("native/name", "thruster", List.of("main \"engine\""), List.of(), List.of());
        String code = LuaCompletions.wrapSnippet(named, "");
        assertTrue(code.contains("peripheral.wrap(\"main \\\"engine\\\"\")"));
        assertEquals(named, ScriptPeripheral.fromTag(named.toTag()));
        var reserved = new ScriptPeripheral("native/ports", "thruster", List.of("ports"), List.of(), List.of());
        assertEquals("local thruster = peripheral.wrap(\"ports\")\n", LuaCompletions.wrapSnippet(reserved, ""));
    }

    @Test void localShadowsAndFunctionParametersDoNotChangeOuterBindings(){
        String prefix = "local thruster = peripheral.wrap('thruster')\n";
        assertTrue(suggest(prefix + "function update(thruster) thruster.setTh").values().isEmpty());
        assertTrue(suggest(prefix + "do local thruster = nil\nthruster.setTh").values().isEmpty());
        assertEquals(List.of("thruster.setThrottle("), suggest(prefix + "do local thruster = nil end\nthruster.setTh").values());
        assertEquals(List.of("thruster.setThrottle("), suggest(prefix + "if thruster == nil then print('missing') end\nthruster.setTh").values());
    }
}
