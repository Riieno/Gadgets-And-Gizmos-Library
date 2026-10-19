package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PercentageNodeTest{
    @Test void exposesNumericInputsAndReturnsTheRequestedPercentage(){
        var node = node();
        assertEquals(List.of("a", "b"), List.copyOf(node.definition().inputs().keySet()));
        assertEquals("number", node.definition().outputs().get("value"));
        assertEquals(25, value(50, 200).get("value").asNumber());
        assertTrue(value(50, 200).get("valid").asBoolean());
        assertEquals(100, value(200, 200).get("value").asNumber());
        assertEquals(0, node.executor().execute(null, Map.of()).get("value").asNumber());
        assertTrue(node.executor().execute(null, Map.of()).get("valid").asBoolean());
    }

    @Test void handlesSignedValuesAboveOneHundredAndLargeOperands(){
        assertEquals(-25, value(-50, 200).get("value").asNumber());
        assertEquals(-25, value(50, -200).get("value").asNumber());
        assertEquals(250, value(500, 200).get("value").asNumber());
        assertEquals(100, value(Double.MAX_VALUE, Double.MAX_VALUE).get("value").asNumber());
    }

    @Test void rejectsZeroDenominatorsInvalidInputsAndOverflow(){
        for(double total : new double[]{0, -0.0}){
            var output = value(50, total);
            assertEquals(0, output.get("value").asNumber());
            assertFalse(output.get("valid").asBoolean());
        }
        assertFalse(value(Double.MAX_VALUE, Double.MIN_VALUE).get("valid").asBoolean());
        for(String key : List.of("a", "b")){
            for(double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}){
                var output = node().executor().execute(null, Map.of(key, new GraphValue("number", invalid)));
                assertFalse(output.get("valid").asBoolean());
                assertEquals(0, output.get("value").asNumber());
            }
        }
    }

    private static Map<String, GraphValue> value(double a, double b){
        return node().executor().execute(null, Map.of("a", GraphValue.number(a), "b", GraphValue.number(b)));
    }

    private static MathGraphNodes.Node node(){
        return MathGraphNodes.nodes("test").stream().filter(node -> node.definition().id().equals("test:percentage"))
                .findFirst().orElseThrow();
    }
}
