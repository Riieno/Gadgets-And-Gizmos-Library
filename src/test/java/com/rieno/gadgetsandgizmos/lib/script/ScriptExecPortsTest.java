package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ScriptExecPortsTest{
    @Test void declarationsIgnoreCommentsStringsAndDeduplicateNames(){
        String code = "-- node.addExecIn('ignored')\nlocal s = [[node.addExecOut('ignored')]]\n"
                + "node.addExecIn('turn left')\nnode.addExecIn('turn left')\nnode.addExecOut(\"done\")";
        var ports = ScriptExecPorts.read(code);
        assertEquals(List.of("turn left"), ports.inputs()); assertEquals(List.of("done"), ports.outputs());
        assertEquals(List.of("escaped name"), ScriptExecPorts.read("node.addExecIn('escaped\\032name')").inputs());
        assertEquals(List.of("long name"), ScriptExecPorts.read("node.addExecIn([=[long name]=])").inputs());
    }

    @Test void authoredPortChangesRemainUntilExplicitRemoval(){
        var data = new CompoundTag();
        ScriptPorts.change(data, "start", false, true, false);
        ScriptPorts.change(data, "done", true, true, false);
        ScriptPorts.change(data, "value", false, false, false);
        data.putString(LuaGraphNode.SOURCE, "print('new code')");
        assertFalse(LuaGraphNode.ports(data, false).containsKey("exec"));
        assertEquals("exec", LuaGraphNode.ports(data, false).get("start"));
        assertEquals("exec", LuaGraphNode.ports(data, true).get("done"));
        assertFalse(ScriptPorts.change(data, "start", false, true, false));
        assertEquals("any", LuaGraphNode.ports(data, false).get("value"));
        ScriptPorts.change(data, "start", false, true, true);
        assertFalse(LuaGraphNode.ports(data, false).containsKey("start"));
        assertTrue(ScriptExecPorts.validate(ScriptExecPorts.read("node.addExecOut('busy')"), Set.of(), Set.of()).contains("conflicts"));
        assertTrue(ScriptExecPorts.validate(ScriptExecPorts.read("node.addExecIn('data')"), Set.of("data"), Set.of()).contains("conflicts"));
    }

    @Test void terminalSnapshotsRoundTripAndRejectInvalidDimensionsAndRows(){
        var palette = java.util.Collections.nCopies(16, 0xFF123456);
        var terminal = new ScriptTerminal(3, 1, List.of("Lua"), List.of("012"), List.of("fff"), palette, 1, 0, true, 2);
        assertEquals(terminal, ScriptTerminal.fromValue(terminal.toValue()));
        var typed = new java.util.LinkedHashMap<String, Object>();
        ((java.util.Map<?, ?>)terminal.toValue().value()).forEach((key, val) -> typed.put(key.toString(), com.rieno.gadgetsandgizmos.lib.graph.GraphValue.of(val)));
        assertEquals(terminal, ScriptTerminal.fromValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.map(typed)));
        assertThrows(IllegalArgumentException.class, () -> new ScriptTerminal(0, 1, List.of(), List.of(), List.of(), palette, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScriptTerminal(3, 1, List.of("Lua"), List.of("zzx"), List.of("fff"), palette, 0, 0, false, 0));
    }
}
