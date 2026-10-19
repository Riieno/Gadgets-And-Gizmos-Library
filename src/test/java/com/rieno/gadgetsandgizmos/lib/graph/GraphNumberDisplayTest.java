package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphNumberDisplayTest{
    @Test void preciseValuesRoundTripEveryDoubleIncludingTinyValuesAndSignedZero(){
        for(double value : new double[]{Math.PI, Math.nextUp(1.0), 0.0000000123456789,
                -123456789.12345679, Double.MIN_VALUE, Double.MAX_VALUE, -0.0, Double.POSITIVE_INFINITY, Double.NaN}){
            String text = GraphNumberDisplay.format(value, true);
            assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(Double.parseDouble(text)));
        }
        assertEquals("1.23", GraphNumberDisplay.format(1.23456789, false));
        assertEquals("2", GraphNumberDisplay.format(2, false));
        assertEquals("0", GraphNumberDisplay.format(0.00000001, false));
        assertEquals("1.23456789", GraphNumberDisplay.format(1.23456789, true));
    }

    @Test void numericAliasesAndResolvedAnyPortsShareTheSameDisplayPolicy(){
        for(String type : List.of("number", "int", "integer", "double", "float")){
            assertTrue(GraphNumberDisplay.hasNumericPorts(Map.of("in", type), Map.of(), (port, output) -> "string"));
            assertTrue(GraphNumberDisplay.hasNumericPorts(Map.of(), Map.of("out", type), (port, output) -> "string"));
        }
        assertTrue(GraphNumberDisplay.hasNumericPorts(Map.of("a", "any"), Map.of(), (port, output) -> "number"));
        assertTrue(GraphNumberDisplay.hasNumericPorts(Map.of(), Map.of("value", "any"), (port, output) -> "double"));
        assertFalse(GraphNumberDisplay.hasNumericPorts(Map.of("a", "any"), Map.of(), (port, output) -> "string"));
        assertFalse(GraphNumberDisplay.hasNumericPorts(Map.of("exec", "exec"), Map.of("value", "boolean"), (port, output) -> "number"));
        assertTrue(GraphNumberDisplay.supportsNumericPorts(Map.of("a", "any"), Map.of()));
    }

    @Test void togglesOnlyChangeTheDisplayFlag(){
        CompoundTag data = new CompoundTag();
        data.putDouble("Value", Math.PI);
        CompoundTag original = data.copy();
        assertFalse(GraphNumberDisplay.precise(data));
        GraphNumberDisplay.setPrecise(data, true);
        assertTrue(GraphNumberDisplay.precise(data));
        assertEquals(Math.PI, data.getDouble("Value"));
        GraphNumberDisplay.setPrecise(data, false);
        assertEquals(original, data);
    }
}
