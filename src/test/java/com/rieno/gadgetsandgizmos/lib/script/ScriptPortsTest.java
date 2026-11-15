package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptPortsTest{
    @Test void declarationsOwnOnlyTheirProgrammaticPorts(){
        var data = new CompoundTag(); ScriptPorts.change(data, "manual", true, false, false);
        data.putBoolean(ScriptPorts.TRACKED, true);
        String source = "node.addInputPort('sensor_' .. tostring(6))\nnode.addOutputPort('temperature')\nnode.addExecOut('received')";
        var declarations = ScriptPortDeclarations.read(source);
        assertEquals(3, declarations.size());
        ScriptPorts.programmatic(data, "sensor_6", false, false, false, declarations.getFirst().key());
        ScriptPorts.programmatic(data, "temperature", true, false, false, declarations.get(1).key());
        ScriptPorts.programmatic(data, "received", true, true, false, declarations.getLast().key());
        assertFalse(ScriptPorts.reconcile(data, source, "-- heading\n" + source));
        assertTrue(ScriptPorts.reconcile(data, source, "node.addOutputPort('temperature')"));
        assertEquals(List.of("temperature"), data.getCompound("DynamicOutputs").getAllKeys().stream().filter(key -> !key.equals("manual")).toList());
        assertTrue(data.getCompound("DynamicInputs").isEmpty()); assertTrue(data.getCompound(ScriptPorts.EXEC_OUTPUTS).isEmpty());
        assertTrue(ScriptPorts.reconcile(data, "node.addOutputPort('temperature')", "-- node.addOutputPort('temperature')"));
        assertEquals(java.util.Set.of("manual"), data.getCompound("DynamicOutputs").getAllKeys());
    }

    @Test void legacyLiteralPortsAndExecPortsMigrateWithoutDeletingUiDataPorts(){
        var data = new CompoundTag(); ScriptPorts.change(data, "manual", true, false, false);
        ScriptPorts.change(data, "old", true, false, false); ScriptPorts.change(data, "stale", true, true, false);
        assertTrue(ScriptPorts.reconcile(data, "node.addOutputPort('old')", ""));
        assertEquals(java.util.Set.of("manual"), data.getCompound("DynamicOutputs").getAllKeys());
        assertTrue(data.getCompound(ScriptPorts.EXEC_OUTPUTS).isEmpty());
        assertTrue(ScriptPortDeclarations.read("local s = [[node.addExecOut('fake')]] -- node.addInputPort('fake')").isEmpty());
    }

    @Test void terminalPointsMatchTheFittedNativeCellsAndValidateInputs(){
        assertNull(ScriptInput.point(0, 0, 600, 200, 51, 19));
        var point = ScriptInput.point(300, 100, 600, 200, 51, 19);
        var input = new ScriptInput("click", point[0], point[1], 0);
        assertArrayEquals(new Object[]{1, 26, 10}, input.arguments(51, 19));
        assertEquals("mouse_click", input.event());
        assertThrows(IllegalArgumentException.class, () -> new ScriptInput("key", 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new ScriptInput("char", 0, 0, 0));
    }

    @Test void referencingAnAuthoredPortInCodeDoesNotTransferItsOwnership(){
        var data = new CompoundTag(); data.putBoolean(ScriptPorts.TRACKED, true);
        ScriptPorts.change(data, "manual", true, false, false);
        ScriptPorts.programmatic(data, "manual", true, false, false, "addOutputPort\u0000'manual'");
        assertFalse(ScriptPorts.reconcile(data, "node.addOutputPort('manual')", ""));
        assertTrue(data.getCompound("DynamicOutputs").contains("manual"));
    }
}
