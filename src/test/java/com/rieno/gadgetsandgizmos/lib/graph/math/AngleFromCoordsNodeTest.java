package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AngleFromCoordsNodeTest{
    private static final MathGraphNodes.Node NODE = MathGraphNodes.nodes("test").stream()
            .filter(node -> node.definition().id().equals("test:angle_from_coords")).findFirst().orElseThrow();

    @Test void exposesTheRequestedPortsAndCoordinateDefaults(){
        assertEquals("Angle from Coords", NODE.title());
        assertEquals(List.of("origin", "target"), List.copyOf(NODE.definition().inputs().keySet()));
        assertEquals(List.of("euler", "quaternion", "tait_bryan", "axis", "angle", "degrees", "valid"),
                List.copyOf(NODE.definition().outputs().keySet()));
        assertEquals(Vector3.ZERO, MathGraphValues.vector(NODE.defaults().get("origin")));
        assertEquals(new Vector3(0, 0, 1), MathGraphValues.vector(NODE.defaults().get("target")));
        assertTrue(NODE.executor().execute(null, Map.of()).get("valid").asBoolean());
    }

    @Test void allRepresentationsDescribeTheSameRotationAcrossOctantsAndPoles(){
        for(int x = -2; x <= 2; x++) for(int y = -2; y <= 2; y++) for(int z = -2; z <= 2; z++){
            if(x == 0 && y == 0 && z == 0) continue;
            Vector3 target = new Vector3(x, y, z);
            var outputs = NODE.executor().execute(null, Map.of("origin", MathGraphValues.vector(Vector3.ZERO),
                    "target", MathGraphValues.vector(target)));
            assertTrue(outputs.get("valid").asBoolean());
            Quaternion rotation = MathGraphValues.quaternion(outputs.get("quaternion"));
            assertRotation(rotation, RotationMath.eulerZxzToQuaternion(MathGraphValues.euler(outputs.get("euler"))));
            assertRotation(rotation, RotationMath.taitBryanXyzToQuaternion(MathGraphValues.taitBryan(outputs.get("tait_bryan"))));
            Vector3 axis = MathGraphValues.vector(outputs.get("axis"));
            double angle = outputs.get("angle").asNumber();
            assertEquals(1, axis.magnitude(), 1.0E-12);
            assertTrue(angle >= 0 && angle <= Math.PI);
            assertEquals("number", outputs.get("degrees").type());
            double heading = outputs.get("degrees").asNumber();
            assertTrue(heading >= 0 && heading < 360);
            if(x == 0 && z == 0) assertEquals(0, heading);
            else{
                Vector3 horizontal = Quaternion.fromAxisAngle(new Vector3(0, 1, 0), Math.toRadians(heading)).rotate(new Vector3(0, 0, 1));
                Vector3 expected = new Vector3(x, 0, z).normalized();
                assertEquals(expected.x(), horizontal.x(), 1.0E-10);
                assertEquals(expected.z(), horizontal.z(), 1.0E-10);
            }
            assertRotation(rotation, Quaternion.fromAxisAngle(axis, angle));
            assertEquals(Math.acos(target.normalized().z()), angle, 1.0E-10);
        }
    }

    @Test void acceptsUppercaseCoordinateMapFields(){
        var outputs = NODE.executor().execute(null, Map.of("origin", GraphValue.map(Map.of("X", 12, "Y", 4, "Z", -9)),
                "target", GraphValue.map(Map.of("X", 15, "Y", 4, "Z", -9))));
        assertTrue(outputs.get("valid").asBoolean());
        assertEquals(Math.PI / 2, outputs.get("angle").asNumber(), 1.0E-12);
        Vector3 axis = MathGraphValues.vector(outputs.get("axis"));
        assertEquals(0, axis.x(), 1.0E-12);assertEquals(1, axis.y(), 1.0E-12);assertEquals(0, axis.z(), 1.0E-12);
    }

    @Test void degenerateInputsReturnTypedEmptyOutputsWithoutNan(){
        for(GraphValue target : List.of(MathGraphValues.vector(Vector3.ZERO), GraphValue.map(Map.of("x", Double.NaN)))){
            var outputs = NODE.executor().execute(null, Map.of("origin", MathGraphValues.vector(Vector3.ZERO), "target", target));
            assertFalse(outputs.get("valid").asBoolean());
            assertEquals(0, outputs.get("angle").asNumber());
            assertEquals(0, outputs.get("degrees").asNumber());
            for(String port : List.of("euler", "quaternion", "tait_bryan", "axis")){
                assertEquals("map", outputs.get(port).type());
                assertEquals(Map.of(), outputs.get(port).value());
            }
        }
    }

    private static void assertRotation(Quaternion expected, Quaternion actual){
        assertEquals(1, Math.abs(expected.normalized().dot(actual.normalized())), 1.0E-10);
    }
}
