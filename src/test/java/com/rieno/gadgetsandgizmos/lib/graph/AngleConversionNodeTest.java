package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode;
import com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.Format;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Keep cardinal strings aligned with world coordinates across angle units and rotation formats
class AngleConversionNodeTest{
    // Resolve scalar headings independently of the optional rotation axis
    @Test void scalarFormatsReturnAllEightWorldCardinals(){
        List<String> expected = List.of("South", "South East", "East", "North East", "North", "North West", "West", "South West");
        for(Format format : List.of(Format.RADIANS, Format.DEGREES, Format.TURNS, Format.GRADIANS)){
            for(int idx = 0; idx < expected.size(); idx++){
                double angle = switch(format){
                    case RADIANS -> idx * Math.PI / 4;
                    case DEGREES -> idx * 45.0D;
                    case TURNS -> idx / 8.0D;
                    default -> idx * 50.0D;
                };
                var output = AngleConversionNode.execute(format, Format.CARDINAL, Map.of("angle", GraphValue.number(angle)));
                assertTrue(output.get("valid").asBoolean());
                assertEquals(GraphValue.string(expected.get(idx)), output.get("cardinal"));
            }
        }
    }

    // Preserve the same compass direction when full 3D rotations pass through other formats
    @Test void rotationsResolveWorldDirectionsAfterPitchAndFormatConversion(){
        Vector3 origin = new Vector3(14, 8, -23);
        List<Vector3> directions = List.of(new Vector3(0, 3, -1), new Vector3(1, 3, -1),
                new Vector3(1, 3, 0), new Vector3(1, 3, 1), new Vector3(0, 3, 1),
                new Vector3(-1, 3, 1), new Vector3(-1, 3, 0), new Vector3(-1, 3, -1));
        List<String> expected = List.of("North", "North East", "East", "South East", "South", "South West", "West", "North West");
        for(int idx = 0; idx < directions.size(); idx++){
            Quaternion rotation = RotationMath.rotationFromCoordinates(origin, origin.add(directions.get(idx)));
            for(Format format : List.of(Format.AXIS_ANGLE, Format.EULER, Format.TAIT_BRYAN, Format.QUATERNION)){
                var converted = AngleConversionNode.execute(Format.QUATERNION, format,
                        Map.of("quaternion", MathGraphValues.quaternion(rotation)));
                var output = AngleConversionNode.execute(format, Format.CARDINAL, converted);
                assertTrue(output.get("valid").asBoolean());
                assertEquals(expected.get(idx), output.get("cardinal").asString(), format.id());
            }
        }
    }

    // Wrap signed and repeated turns and select the nearest compass sector at its boundaries
    @Test void headingsWrapAndRoundAtCompassBoundaries(){
        for(double degrees : new double[]{0, 360, -360, 720}) assertEquals("South", cardinal(degrees));
        for(double degrees : new double[]{90, 450, -270}) assertEquals("East", cardinal(degrees));
        assertEquals("South East", cardinal(22.5001D));
        assertEquals("South", cardinal(22.4999D));
        assertEquals("South West", cardinal(-22.5001D));
        assertEquals("South", cardinal(-22.4999D));
        assertEquals("North", cardinal(179.999D));
        assertEquals("North", cardinal(-179.999D));
    }

    // Keep unavailable headings typed as empty strings instead of reporting a false direction
    @Test void verticalAndInvalidRotationsReturnAnInvalidString(){
        for(Quaternion rotation : List.of(new Quaternion(0, 0, 0, 0),
                Quaternion.fromAxisAngle(new Vector3(1, 0, 0), Math.PI / 2))){
            var output = AngleConversionNode.execute(Format.QUATERNION, Format.CARDINAL,
                    Map.of("quaternion", MathGraphValues.quaternion(rotation)));
            assertEquals(GraphValue.string(""), output.get("cardinal"));
            assertFalse(output.get("valid").asBoolean());
        }
        var invalid = AngleConversionNode.execute(Format.DEGREES, Format.CARDINAL,
                Map.of("angle", GraphValue.string("North")));
        assertEquals(GraphValue.string(""), invalid.get("cardinal"));
        assertFalse(invalid.get("valid").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> RotationMath.horizontalCardinal(Double.NaN));
    }

    // Add Cardinal only to outputs while retaining numeric conversion and saved input fallbacks
    @Test void cardinalIsOutputOnlyAndExistingConversionsStayNumeric(){
        assertTrue(Format.outputOptionIds().contains("cardinal"));
        assertFalse(Format.inputOptionIds().contains("cardinal"));
        assertEquals(Format.RADIANS, Format.fromInputId("cardinal"));
        assertEquals(Map.of("cardinal", "string", "valid", "boolean"), AngleConversionNode.outputs(Format.CARDINAL));
        var output = AngleConversionNode.execute(Format.DEGREES, Format.RADIANS, Map.of("angle", GraphValue.number(-450)));
        assertTrue(output.get("valid").asBoolean());
        assertEquals(-2.5D * Math.PI, output.get("angle").asNumber(), 1.0E-9D);
        assertEquals("number", output.get("angle").type());
    }

    // Read the production node's scalar heading output
    private static String cardinal(double degrees){
        return AngleConversionNode.execute(Format.DEGREES, Format.CARDINAL,
                Map.of("angle", GraphValue.number(degrees))).get("cardinal").asString();
    }
}
