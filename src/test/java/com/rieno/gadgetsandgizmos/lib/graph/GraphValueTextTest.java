package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphValueTextTest{
    @Test void anyLiteralsRetainTheirConcreteTypeAndZeroValues(){
        assertEquals(GraphValue.number(0), GraphValueText.parse("any", "0"));
        assertEquals(GraphValue.bool(false), GraphValueText.parse("any", "false"));
        assertEquals(GraphValue.string(""), GraphValueText.parse("string", ""));
        assertEquals(GraphValue.string("001"), GraphValueText.parse("string", "001"));
        assertThrows(IllegalArgumentException.class, () -> GraphValueText.parse("number", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> GraphValueText.parse("map", "[]"));
    }

    @Test void nestedStructuredValuesRoundTripInAuthoredOrder(){
        String text = "{\"z\":0,\"a\":[false,{\"name\":\"test\"}]}";
        var value = GraphValueText.parse("map", text);
        assertEquals(List.of("z", "a"), ((java.util.Map<?, ?>)value.value()).keySet().stream().toList());
        String formatted = GraphValueText.format(value);
        assertTrue(formatted.indexOf("\"z\"") < formatted.indexOf("\"a\""));
        assertEquals(value, GraphValueText.parse("map", GraphValueText.format(value)));
    }
}
