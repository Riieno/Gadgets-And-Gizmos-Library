package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphNodes;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphPortOrderTest{
    @Test void definitionsAndMathSchemasRetainDeclarationOrder(){
        var inputs = GraphPortOrder.ports("Y", "number", "X", "number");
        var definition = new GraphNodeDefinition("test:atan2", "math", inputs, Map.of(), false);
        assertEquals(List.of("Y", "X"), List.copyOf(definition.inputs().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> definition.inputs().put("z", "number"));
        assertEquals(List.of("euler", "alpha", "beta", "gamma", "valid"),
                List.copyOf(MathGraphNodes.rotationNode("quaternion_to_euler").definition().outputs().keySet()));
        assertEquals(List.of("x", "y", "z", "w"),
                List.copyOf(((Map<?, ?>)MathGraphValues.quaternion(new Quaternion(1, 2, 3, 4)).value()).keySet()));
    }

    @Test void savedOrderOverridesNbtHashOrderAndTypeChangesDoNotMovePorts(){
        CompoundTag dynamic = new CompoundTag();
        dynamic.putString("alpha", "number");dynamic.putString("zeta", "string");dynamic.putString("Y", "double");
        CompoundTag data = new CompoundTag();
        GraphPortOrder.write(data, GraphPortOrder.INPUT_ORDER, List.of("Y", "X", "zeta", "alpha", "retired"));
        var ports = GraphPortOrder.merge(GraphPortOrder.ports("Y", "number", "X", "number"), dynamic,
                GraphPortOrder.read(data.copy(), GraphPortOrder.INPUT_ORDER));
        assertEquals(List.of("Y", "X", "zeta", "alpha"), List.copyOf(ports.keySet()));
        assertEquals("double", ports.get("Y"));
    }

    @Test void legacyNumberedPortsUseNumericOrderWithoutAlphabetizingNamedFields(){
        assertEquals(List.of("0", "2", "10", "11"), GraphPortOrder.numberedKeys(List.of("11", "2", "0", "10")));
        assertEquals(List.of("zeta", "value_1", "alpha", "value_2", "value_10"),
                GraphPortOrder.numberedKeys(List.of("zeta", "value_10", "alpha", "value_2", "value_1")));
        assertEquals(List.of("n_2", "n_99999999999999999999999999"),
                GraphPortOrder.numberedKeys(List.of("n_99999999999999999999999999", "n_2")));
        assertEquals(List.of("parent", "zeta", "alpha", "other"),
                GraphPortOrder.withChildren(List.of("parent", "other", "alpha"), "parent", List.of("zeta", "alpha")));
    }

    @Test void graphValuesKeepNestedMapOrderAndRemainImmutable(){
        Map<String, Object> fields = new LinkedHashMap<>();fields.put("zeta", 3);fields.put("alpha", 1);
        var value = GraphValue.map(fields);
        fields.put("middle", 2);
        assertEquals(List.of("zeta", "alpha"), List.copyOf(((Map<?, ?>)value.value()).keySet()));
        var nested = GraphValue.map(GraphPortOrder.entries(Map.entry("outer", value)));
        assertEquals(List.of("zeta", "alpha"), List.copyOf(((Map<?, ?>)nested.member("outer").value()).keySet()));
    }

    @Test void refreshedSchemasRetainSourceOrderAndAppendConnectedMissingFields(){
        var ports = GraphDynamicPortSchema.retainConnectedOutputs(
                GraphPortOrder.ports("zeta", "number", "alpha", "string"),
                GraphPortOrder.ports("alpha", "number", "kept", "boolean", "removed", "string"), List.of("kept"));
        assertEquals(List.of("zeta", "alpha", "kept"), List.copyOf(ports.keySet()));
        assertEquals("string", ports.get("alpha"));
    }

    @Test void frameSectionsUseCanvasScaleAndFitShortNodes(){
        for(double scale : new double[]{0.65, 1, 1.75}){
            var frame = GraphNodeLayout.frame(300, scale, 23, 8);
            assertEquals((int)Math.round(23 * scale), frame.titleHeight());
            assertEquals(300, frame.titleHeight() + frame.bodyHeight() + frame.footerHeight());
        }
        assertEquals(new GraphNodeLayout.Frame(4, 0, 0), GraphNodeLayout.frame(4, 1, 23, 8));
    }
}
