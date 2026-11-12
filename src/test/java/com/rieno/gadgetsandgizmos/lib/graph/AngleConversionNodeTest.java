package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.control.math.AngleUnit;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode;
import com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.Format;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphNodes;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Verify degree components, rotation round trips and world cardinal directions
class AngleConversionNodeTest{
    // Resolve known rotations as proper ZYZ Euler components in degrees
    @Test void eulerOutputsAreProperZyzDegrees(){
        List<Vector3> axes = List.of(new Vector3(0, 1, 0), new Vector3(1, 0, 0), new Vector3(0, 0, 1));
        List<String> ports = List.of("alpha", "beta", "gamma");
        List<Vector3> expected = List.of(new Vector3(0, 60, 0), new Vector3(-90, 60, 90), new Vector3(60, 0, 0));
        for(int idx = 0; idx < axes.size(); idx++){
            var out = AngleConversionNode.execute(Format.DEGREES, Format.EULER,
                    Map.of("angle", GraphValue.number(60), "axis", MathGraphValues.vector(axes.get(idx))));
            assertTrue(out.get("valid").asBoolean());
            for(int component = 0; component < 3; component++){
                double val = List.of(expected.get(idx).x(), expected.get(idx).y(), expected.get(idx).z()).get(component);
                assertEquals(val, out.get(ports.get(component)).asNumber(), 1.0E-8D);
                assertEquals(val, MathGraphValues.component(out.get("euler"), ports.get(component), Double.NaN), 1.0E-8D);
                assertEquals("number", out.get(ports.get(component)).type());
            }
            assertMetadata(out.get("euler"), "euler", "zyz", "degrees");
            assertFalse(((Map<?, ?>)out.get("euler").value()).containsKey("yaw"));
        }
        assertEquals("Euler (ZYZ)", Format.EULER.displayName());
        assertEquals("Tait-Bryan (XYZ)", Format.TAIT_BRYAN.displayName());
        assertEquals("Alpha (Z, deg)", AngleConversionNode.outputLabel(Format.EULER, "alpha"));
        assertEquals("Beta (Y, deg)", AngleConversionNode.outputLabel(Format.EULER, "beta"));
        assertEquals("Gamma (Z, deg)", AngleConversionNode.outputLabel(Format.EULER, "gamma"));
    }

    // Compare each format against independently composed axis rotations
    @Test void namedDegreeMapsComposeTheExpectedRotations(){
        var euler = GraphValue.map(Map.of("alpha", 42D, "beta", 65D, "gamma", -23D));
        var tait = GraphValue.map(Map.of("roll", 23D, "pitch", -31D, "yaw", 42D));
        Quaternion eulerExpected = axis(0, 0, 1, 42).multiply(axis(0, 1, 0, 65)).multiply(axis(0, 0, 1, -23));
        Quaternion taitExpected = axis(0, 0, 1, 42).multiply(axis(0, 1, 0, -31)).multiply(axis(1, 0, 0, 23));
        assertRotation(eulerExpected, AngleConversionNode.execute(Format.EULER, Format.QUATERNION, Map.of("euler", euler)));
        assertRotation(taitExpected, AngleConversionNode.execute(Format.TAIT_BRYAN, Format.QUATERNION, Map.of("tait_bryan", tait)));
        var out = AngleConversionNode.execute(Format.QUATERNION, Format.TAIT_BRYAN, Map.of("quaternion", MathGraphValues.quaternion(taitExpected)));
        assertEquals(23, out.get("roll").asNumber(), 1.0E-8D);
        assertEquals(-31, out.get("pitch").asNumber(), 1.0E-8D);
        assertEquals(42, out.get("yaw").asNumber(), 1.0E-8D);
    }

    // Preserve full rotations at proper Euler and Tait-Bryan singularities
    @Test void degreeMapsRoundTripAtGimbalLockAndAcrossAllAxes(){
        for(double a : new double[]{-180, -45, 0, 32, 180}){
            for(double b : new double[]{-180, -90, -89.999, -20, 0, 0.000001, 50, 89.999, 90, 179.999999, 180}){
                for(double c : new double[]{-160, 0, 65}){
                    for(Format from : List.of(Format.EULER, Format.TAIT_BRYAN)){
                        Quaternion expected = from == Format.EULER
                                ? axis(0, 0, 1, a).multiply(axis(0, 1, 0, b)).multiply(axis(0, 0, 1, c))
                                : axis(0, 0, 1, c).multiply(axis(0, 1, 0, b)).multiply(axis(1, 0, 0, a));
                        for(Format format : List.of(Format.EULER, Format.TAIT_BRYAN)){
                            var converted = AngleConversionNode.execute(Format.QUATERNION, format,
                                    Map.of("quaternion", MathGraphValues.quaternion(expected)));
                            if(format == Format.EULER){
                                assertTrue(converted.get("beta").asNumber() >= 0 && converted.get("beta").asNumber() <= 180);
                                if(Math.abs(Math.sin(Math.toRadians(b))) < 1.0E-10D && from == Format.EULER)
                                    assertEquals(0, converted.get("gamma").asNumber(), 1.0E-8D);
                            }
                            assertRotation(expected, AngleConversionNode.execute(format, Format.QUATERNION, converted));
                        }
                    }
                }
            }
        }
    }

    // Accept tagged legacy radian maps while new output maps identify their degree units
    @Test void legacyRotationMapsAndExplicitUnitsRemainUsable(){
        Vector3 radians = new Vector3(0.3D, 0.6D, -0.4D);
        assertRotation(RotationMath.eulerZyzToQuaternion(radians), AngleConversionNode.execute(Format.EULER, Format.QUATERNION,
                Map.of("euler", MathGraphValues.euler(radians))));
        assertRotation(RotationMath.taitBryanXyzToQuaternion(radians), AngleConversionNode.execute(Format.TAIT_BRYAN, Format.QUATERNION,
                Map.of("tait_bryan", MathGraphValues.taitBryan(radians))));
        var untagged = GraphValue.map(Map.of("alpha", 0.3D, "beta", 0.6D, "gamma", -0.4D));
        var zxz = MathGraphValues.rotationAngles(untagged, AngleUnit.RADIANS, "zxz");
        assertRotation(RotationMath.eulerZxzToQuaternion(radians), AngleConversionNode.execute(Format.EULER, Format.QUATERNION, Map.of("euler", zxz)));
        assertRotation(axis(0, 0, 1, 0.3).multiply(axis(0, 1, 0, 0.6)).multiply(axis(0, 0, 1, -0.4)),
                AngleConversionNode.execute(Format.EULER, Format.QUATERNION, Map.of("euler", untagged)));
        var radiansMap = MathGraphValues.yawPitchRoll(radians);
        assertMetadata(radiansMap, "tait_bryan", "yxz", "radians");
        assertRotation(RotationMath.yawPitchRollToQuaternion(radians), AngleConversionNode.execute(Format.EULER, Format.QUATERNION, Map.of("euler", radiansMap)));
        assertRotation(RotationMath.yawPitchRollToQuaternion(radians), AngleConversionNode.execute(Format.TAIT_BRYAN, Format.QUATERNION, Map.of("tait_bryan", radiansMap)));
        var legacy = GraphValue.map(Map.of("yaw", 42D, "pitch", -31D, "roll", 23D));
        assertRotation(axis(0, 1, 0, 42).multiply(axis(1, 0, 0, -31)).multiply(axis(0, 0, 1, 23)),
                AngleConversionNode.execute(Format.EULER, Format.QUATERNION, Map.of("euler", legacy)));
        for(Format format : List.of(Format.EULER, Format.TAIT_BRYAN)){
            var converted = AngleConversionNode.execute(Format.QUATERNION, format, Map.of("quaternion", MathGraphValues.quaternion(Quaternion.IDENTITY)));
            var map = (Map<?, ?>)converted.get(format.id()).value();
            assertEquals(GraphValue.string("degrees"), map.get("unit"));
        }
        var converted = AngleConversionNode.execute(Format.RADIANS, Format.DEGREES, Map.of("angles", MathGraphValues.taitBryan(radians)));
        assertRotation(RotationMath.taitBryanXyzToQuaternion(radians), AngleConversionNode.execute(Format.TAIT_BRYAN, Format.QUATERNION,
                Map.of("tait_bryan", converted.get("angles"))));
        var degreesMap = MathGraphValues.convertAngles(MathGraphValues.taitBryan(radians), false);
        assertRotation(RotationMath.taitBryanXyzToQuaternion(radians), AngleConversionNode.execute(Format.TAIT_BRYAN, Format.QUATERNION,
                Map.of("tait_bryan", degreesMap)));
    }

    // Preserve orientation and concrete port types across every supported conversion pair
    @Test void everyFormatPairReturnsTheDeclaredTypesAndRotation(){
        Quaternion expected = axis(0, 0, 1, 42).multiply(axis(0, 1, 0, 65)).multiply(axis(0, 0, 1, -23));
        for(Format from : Format.values()){
            if(!from.supportsInput()) continue;
            var input = AngleConversionNode.execute(Format.QUATERNION, from, Map.of("quaternion", MathGraphValues.quaternion(expected)));
            for(Format to : Format.values()){
                var out = AngleConversionNode.execute(from, to, input);
                assertTrue(out.get("valid").asBoolean(), from + " to " + to);
                assertEquals(AngleConversionNode.outputs(to).keySet(), out.keySet());
                AngleConversionNode.outputs(to).forEach((key, type) -> assertEquals(type, out.get(key).type(), key));
                if(to.supportsInput()) assertRotation(expected, AngleConversionNode.execute(to, Format.QUATERNION, out));
            }
        }
    }

    // Share tagged rotations between coordinate, standalone and selectable converter nodes
    @Test void coordinateAndLegacyConvertersUseProperEulerAndTaggedUnits(){
        var coords = MathGraphNodes.nodes("test").stream().filter(node -> node.definition().id().endsWith(":angle_from_coords")).findFirst().orElseThrow();
        var values = coords.executor().execute(null, Map.of("target", MathGraphValues.vector(new Vector3(3, -2, 4))));
        Quaternion expected = MathGraphValues.quaternion(values.get("quaternion"));
        assertMetadata(values.get("euler"), "euler", "zyz", "radians");
        assertMetadata(values.get("tait_bryan"), "tait_bryan", "xyz", "radians");
        assertRotation(expected, AngleConversionNode.execute(Format.EULER, Format.QUATERNION, values));
        for(String id : List.of("quaternion_to_euler", "quaternion_to_tait_bryan", "euler_to_quaternion", "tait_bryan_to_quaternion",
                "euler_to_tait_bryan", "tait_bryan_to_euler")){
            var node = MathGraphNodes.rotationNode(id);
            var out = node.executor().execute(null, values);
            assertTrue(out.get("valid").asBoolean(), id);
            node.definition().outputs().forEach((key, type) -> assertEquals(type, out.get(key).type(), id + ": " + key));
            Format format = id.endsWith("quaternion") ? Format.QUATERNION : id.endsWith("euler") ? Format.EULER : Format.TAIT_BRYAN;
            assertRotation(expected, AngleConversionNode.execute(format, Format.QUATERNION, out));
            if(format == Format.EULER){
                assertMetadata(out.get("euler"), "euler", "zyz", "radians");
                for(String key : List.of("alpha", "beta", "gamma"))
                    assertEquals(MathGraphValues.component(out.get("euler"), key, Double.NaN), out.get(key).asNumber(), 1.0E-8D);
            }
            assertTrue(node.executor().execute(null, Map.of()).get("valid").asBoolean(), id + " identity default");
        }
        for(Format format : List.of(Format.EULER, Format.TAIT_BRYAN)){
            var degrees = AngleConversionNode.execute(Format.QUATERNION, format, values);
            assertRotation(expected, MathGraphNodes.rotationNode(format.id() + "_to_quaternion").executor().execute(null, degrees));
        }
    }

    // Reject mismatched rotation metadata and nonnumeric components with typed invalid outputs
    @Test void malformedRotationMapsCannotMasqueradeAsAnotherType(){
        for(GraphValue map : List.of(GraphValue.map(Map.of("alpha", "60")), GraphValue.map(Map.of("beta", true)),
                GraphValue.map(Map.of("convention", "unknown")), GraphValue.map(Map.of("unit", "unknown")),
                GraphValue.map(Map.of("convention", "zyz", "representation", "tait_bryan")))){
            for(Format to : Format.values()){
                var out = AngleConversionNode.execute(Format.EULER, to, Map.of("euler", map));
                assertFalse(out.get("valid").asBoolean());
                AngleConversionNode.outputs(to).forEach((key, type) -> assertEquals(type, out.get(key).type(), key));
            }
        }
    }

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

    private static Quaternion axis(double x, double y, double z, double degrees){
        return Quaternion.fromAxisAngle(new Vector3(x, y, z), Math.toRadians(degrees));
    }

    private static void assertMetadata(GraphValue val, String kind, String convention, String unit){
        assertEquals("map", val.type());
        assertEquals(kind, val.member("representation").asString());
        assertEquals(convention, val.member("convention").asString());
        assertEquals(unit, val.member("unit").asString());
    }

    // Compare rotated basis vectors so quaternion sign and equivalent singular angle triples do not matter
    private static void assertRotation(Quaternion expected, Map<String, GraphValue> out){
        assertTrue(out.get("valid").asBoolean());
        Quaternion actual = MathGraphValues.quaternion(out.get("quaternion"));
        for(Vector3 basis : List.of(new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)))
            assertEquals(0, expected.rotate(basis).distance(actual.rotate(basis)), 1.0E-8D);
    }
}
