package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AngleUnitConversionNodeTest{
    @Test void exposesNumericPortsWithoutRemovingSavedMapPorts(){
        for(String id : List.of("degrees_to_radians", "radians_to_degrees")){
            var node = node(id);
            assertEquals(List.of("angle", "angles"), List.copyOf(node.definition().inputs().keySet()));
            assertEquals(List.of("angle", "angles", "valid"), List.copyOf(node.definition().outputs().keySet()));
            assertEquals("number", node.definition().inputs().get("angle"));
            assertEquals("number", node.definition().outputs().get("angle"));
            assertEquals("map", node.definition().inputs().get("angles"));
            assertEquals("map", node.definition().outputs().get("angles"));
            assertEquals(0, node.defaults().get("angle").asNumber());
            var outputs = node.executor().execute(null, Map.of());
            assertTrue(outputs.get("valid").asBoolean());
            assertEquals(0, outputs.get("angle").asNumber());
            assertTrue(outputs.get("angles").value() instanceof Map<?, ?> map && map.isEmpty());
        }
    }

    @Test void convertsSignedNumbersAndMultipleTurnsWithoutClamping(){
        double[] degrees = {-720, -180, -90, -45, 0, 45, 90, 180, 720};
        double[] radians = {-4 * Math.PI, -Math.PI, -Math.PI / 2, -Math.PI / 4, 0, Math.PI / 4, Math.PI / 2, Math.PI, 4 * Math.PI};
        for(int idx = 0; idx < degrees.length; idx++){
            assertAngle("degrees_to_radians", degrees[idx], radians[idx]);
            assertAngle("radians_to_degrees", radians[idx], degrees[idx]);
        }
    }

    @Test void numericAndMapConversionsRunIndependentlyAndPreserveNestedValues(){
        GraphValue input = GraphValue.map(Map.of("x", 90, "nested", Map.of("yaw", -180),
                "samples", List.of(45, "keep", true), "label", "heading"));
        var radians = node("degrees_to_radians").executor().execute(null,
                Map.of("angle", GraphValue.number(-450), "angles", input));
        assertTrue(radians.get("valid").asBoolean());
        assertEquals(-2.5 * Math.PI, radians.get("angle").asNumber(), 1.0E-12);
        var map = radians.get("angles");
        assertEquals(Math.PI / 2, map.member("x").asNumber(), 1.0E-12);
        assertEquals(-Math.PI, map.member("nested").member("yaw").asNumber(), 1.0E-12);
        assertEquals(Math.PI / 4, map.member("samples").entries().getFirst().asNumber(), 1.0E-12);
        assertEquals("keep", map.member("samples").entries().get(1).asString());
        assertTrue(map.member("samples").entries().get(2).asBoolean());
        assertEquals("heading", map.member("label").asString());
        var restored = node("radians_to_degrees").executor().execute(null,
                Map.of("angle", radians.get("angle"), "angles", map));
        assertTrue(restored.get("valid").asBoolean());
        assertEquals(-450, restored.get("angle").asNumber(), 1.0E-10);
        assertEquals(90, restored.get("angles").member("x").asNumber(), 1.0E-10);
        assertEquals(-180, restored.get("angles").member("nested").member("yaw").asNumber(), 1.0E-10);
    }

    @Test void oldMapOnlyGraphsStillConvertWithoutANumericInput(){
        for(String id : List.of("degrees_to_radians", "radians_to_degrees")){
            boolean toRadians = id.equals("degrees_to_radians");
            var outputs = node(id).executor().execute(null,
                    Map.of("angles", GraphValue.map(Map.of("yaw", toRadians ? 180 : Math.PI))));
            assertTrue(outputs.get("valid").asBoolean());
            assertEquals(toRadians ? Math.PI : 180, outputs.get("angles").member("yaw").asNumber(), 1.0E-12);
            assertEquals(0, outputs.get("angle").asNumber());
        }
    }

    @Test void invalidNumbersAndOverflowClearOutputs(){
        for(String id : List.of("degrees_to_radians", "radians_to_degrees")){
            for(double angle : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}){
                var outputs = node(id).executor().execute(null, Map.of("angle", new GraphValue("number", angle)));
                assertFalse(outputs.get("valid").asBoolean());
                assertEquals(0, outputs.get("angle").asNumber());
                assertTrue(((Map<?, ?>)outputs.get("angles").value()).isEmpty());
            }
        }
        assertFalse(node("radians_to_degrees").executor().execute(null,
                Map.of("angle", GraphValue.number(Double.MAX_VALUE))).get("valid").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> MathGraphValues.convertAngle(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> MathGraphValues.convertAngle(Double.MAX_VALUE, false));
    }

    private static void assertAngle(String id, double input, double expected){
        var outputs = node(id).executor().execute(null, Map.of("angle", GraphValue.number(input)));
        assertTrue(outputs.get("valid").asBoolean());
        assertEquals("number", outputs.get("angle").type());
        assertEquals(expected, outputs.get("angle").asNumber(), 1.0E-10);
    }

    private static MathGraphNodes.Node node(String id){
        return MathGraphNodes.nodes("test").stream()
                .filter(node -> node.definition().id().equals("test:" + id)).findFirst().orElseThrow();
    }
}
