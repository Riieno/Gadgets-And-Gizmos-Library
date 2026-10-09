package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MathGraphNodesTest{
    @Test
    void everyNodeHasTypedOutputsAndUsesTheOwnersNamespace(){
        for(var node : MathGraphNodes.nodes("test_mod")){
            assertTrue(node.definition().id().startsWith("test_mod:"));
            var outputs = node.executor().execute(new Context(), Map.of());
            assertTrue(outputs.get("valid").asBoolean(), node.definition().id());
            node.definition().outputs().forEach((key, type) -> {
                if(!type.equals("exec")) assertEquals(type, outputs.get(key).type(), node.definition().id() + ":" + key);
            });
        }
    }

    @Test
    void rawQuaternionsAndWrappedUppercaseVectorsAreSupported(){
        var product = execute("quaternion_multiply", Map.of("a", MathGraphValues.quaternion(new Quaternion(1, 2, 3, 4)),
                "b", MathGraphValues.quaternion(new Quaternion(-1, -2, -3, 4))));
        assertEquals(new Quaternion(0, 0, 0, 30), MathGraphValues.quaternion(product.get("quaternion")));
        var res = execute("vector_scale", Map.of("vector", GraphValue.map(Map.of("X", GraphValue.number(2), "Y", 3, "Z", -4)),
                "scalar", GraphValue.number(2)));
        assertEquals(new Vector3(4, 6, -8), MathGraphValues.vector(res.get("value")));
    }

    @Test
    void convertersKeepMapNamesAndLabelAnglesWithLegacyAliases(){
        var input = GraphValue.map(Map.of("roll", GraphValue.number(90), "pitch", 30, "yaw", -45,
                "label", "orientation", "nested", Map.of("alpha", 180)));
        var converted = execute("degrees_to_radians", Map.of("angles", input)).get("angles");
        assertEquals(Math.PI / 2, MathGraphValues.component(converted, "roll", 0), 1.0E-12);
        assertEquals("orientation", ((Map<?, ?>) converted.value()).get("label"));
        var back = execute("radians_to_degrees", Map.of("angles", converted)).get("angles");
        assertEquals(90, MathGraphValues.component(back, "roll", 0), 1.0E-12);
        for(String id : new String[]{"quaternion_to_euler", "quaternion_to_tait_bryan"}){
            var node = MathGraphNodes.rotationNode(id);
            var out = node.executor().execute(new Context(), Map.of("quaternion", MathGraphValues.quaternion(
                    Quaternion.fromAxisAngle(new Vector3(0, 0, 1), 0.7))));
            boolean euler = id.endsWith("euler");
            GraphValue map = out.get(euler ? "euler" : "tait_bryan");
            String[] labels = euler ? new String[]{"alpha", "beta", "gamma"} : new String[]{"roll", "pitch", "yaw"};
            for(int idx = 0; idx < 3; idx++){
                assertEquals(MathGraphValues.component(map, labels[idx], -99), out.get(labels[idx]).asNumber(), 1.0E-12);
                assertEquals(MathGraphValues.component(map, labels[idx], -99), MathGraphValues.component(map, "xyz".substring(idx, idx + 1), -99), 1.0E-12);
            }
            String reverse = euler ? "euler_to_quaternion" : "tait_bryan_to_quaternion";
            var roundTrip = MathGraphNodes.rotationNode(reverse).executor().execute(new Context(), Map.of(euler ? "euler" : "tait_bryan", map));
            assertEquals(1, Math.abs(MathGraphValues.quaternion(roundTrip.get("quaternion")).dot(
                    Quaternion.fromAxisAngle(new Vector3(0, 0, 1), 0.7))), 1.0E-12);
        }
    }

    @Test
    void statefulNodesAdvanceOnlyOnExecAndResetToInputs(){
        for(String id : new String[]{"linear_integrator", "rigid_body_integrator"}){
            var node = node(id);
            var ctx = new Context();
            var input = Map.of("velocity", MathGraphValues.vector(new Vector3(2, 0, 0)), "delta_time", GraphValue.number(1));
            assertEquals(Vector3.ZERO, MathGraphValues.vector(node.executor().execute(ctx, input).get("position")));
            assertTrue(ctx.state.isEmpty());
            ctx.executing = true;
            assertEquals(2, MathGraphValues.vector(node.executor().execute(ctx, input).get("position")).x(), 1.0E-12);
            ctx.executing = false;
            for(int idx = 0; idx < 5; idx++) assertEquals(2, MathGraphValues.vector(node.executor().execute(ctx, input).get("position")).x(), 1.0E-12);
            ctx.executing = true;
            assertEquals(4, MathGraphValues.vector(node.executor().execute(ctx, input).get("position")).x(), 1.0E-12);
            Map<String, GraphValue> reset = new HashMap<>(input);
            reset.put("reset", GraphValue.bool(true));
            assertEquals(Vector3.ZERO, MathGraphValues.vector(node.executor().execute(ctx, reset).get("position")));
            assertEquals(2, MathGraphValues.vector(node.executor().execute(ctx, input).get("position")).x(), 1.0E-12);
        }
    }

    @Test
    void invalidMathReportsFalseWithoutPoisoningIntegratorState(){
        assertFalse(execute("matrix_inverse", Map.of("matrix", GraphValue.map(Map.of()))).get("valid").asBoolean());
        assertFalse(execute("quaternion_inverse", Map.of("quaternion", GraphValue.map(Map.of("w", 0)))).get("valid").asBoolean());
        assertFalse(execute("rigid_body_predict", Map.of("steps", GraphValue.number(0))).get("valid").asBoolean());
        var node = node("linear_integrator");
        var ctx = new Context(); ctx.executing = true;
        var input = Map.of("velocity", MathGraphValues.vector(new Vector3(2, 0, 0)), "delta_time", GraphValue.number(1));
        node.executor().execute(ctx, input);
        GraphValue before = ctx.state.get("motion");
        assertFalse(node.executor().execute(ctx, Map.of("delta_time", GraphValue.number(-1))).get("valid").asBoolean());
        assertEquals(before, ctx.state.get("motion"));
    }

    private static MathGraphNodes.Node node(String id){
        return MathGraphNodes.nodes("test_mod").stream().filter(node -> node.definition().id().equals("test_mod:" + id)).findFirst().orElseThrow();
    }

    private static Map<String, GraphValue> execute(String id, Map<String, GraphValue> inputs){
        return node(id).executor().execute(new Context(), inputs);
    }

    private static final class Context implements GraphExecutionContext{
        private final Map<String, GraphValue> state = new HashMap<>();
        private boolean executing;
        public long tick(){ return 1; }
        public boolean executionTriggered(){ return executing; }
        public GraphValue state(String key){ return state.get(key); }
        public void state(String key, GraphValue val){ state.put(key, val); }
    }
}
