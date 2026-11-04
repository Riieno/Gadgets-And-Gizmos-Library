package com.rieno.gadgetsandgizmos.lib.graph.math;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.math.AngleUnit;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Publish angle conversion formats, checked port schemas and execution for any graph host
public final class AngleConversionNode{
    public static final String INPUT_TYPE = "InputAngleType";
    public static final String OUTPUT_TYPE = "OutputAngleType";
    private static final Vector3 DEFAULT_AXIS = new Vector3(1, 0, 0);

    private AngleConversionNode(){}

    public enum Format{
        RADIANS(AngleUnit.RADIANS), DEGREES(AngleUnit.DEGREES), TURNS(AngleUnit.TURNS), GRADIANS(AngleUnit.GRADIANS),
        AXIS_ANGLE(null), EULER(null), TAIT_BRYAN(null), QUATERNION(null);

        private final AngleUnit unit;
        Format(AngleUnit unit){ this.unit = unit; }
        public String id(){ return name().toLowerCase(Locale.ROOT); }
        public boolean scalar(){ return unit != null; }
        public static List<String> optionIds(){ return Arrays.stream(values()).map(Format::id).toList(); }
        public static Format fromId(String id){
            return fromId(id, RADIANS);
        }
        public static Format fromId(String id, Format fallback){
            return Arrays.stream(values()).filter(format -> format.id().equals(id)).findFirst().orElse(fallback);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static MathGraphNodes.Node node(String namespace){
        if(namespace == null || !namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("A mod namespace is required");
        return new MathGraphNodes.Node(new GraphNodeDefinition(namespace + ":convert_angle", "math",
                inputs(Format.RADIANS), outputs(Format.DEGREES), false), (ctx, in) -> {
            GraphValue from = ctx == null ? null : ctx.configuration(INPUT_TYPE);
            GraphValue to = ctx == null ? null : ctx.configuration(OUTPUT_TYPE);
            return execute(Format.fromId(from == null ? null : from.asString(), Format.RADIANS),
                    Format.fromId(to == null ? null : to.asString(), Format.DEGREES), in);
        }, defaults(), "Convert Angle", "Convert radians, degrees, turns, gradians, axis-angle, ZXZ Euler, XYZ Tait-Bryan and XYZW quaternion rotations. "
                + "Scalar angles use Axis (default +X) when creating a rotation. Rotation outputs use radians; scalar outputs from rotations return the shortest angle and its Axis. "
                + "Scalar unit conversions preserve signed angles, multiple turns and numeric fields in the optional Angles map. Angle from Coords Degrees is a horizontal heading; use its Axis and Angle for the full 3D rotation.");
    }

    public static Map<String, String> inputs(Format format){
        Map<String, String> res = new LinkedHashMap<>();
        if(format.scalar() || format == Format.AXIS_ANGLE){
            res.put("angle", "number");res.put("axis", "map");
            if(format.scalar()) res.put("angles", "map");
        }else res.put(format.id(), "map");
        return GraphPortOrder.copy(res);
    }

    public static Map<String, String> outputs(Format format){
        Map<String, String> res = new LinkedHashMap<>(inputs(format));
        if(format == Format.EULER) for(String key : List.of("alpha", "beta", "gamma")) res.put(key, "number");
        if(format == Format.TAIT_BRYAN) for(String key : List.of("roll", "pitch", "yaw")) res.put(key, "number");
        res.put("valid", "boolean");
        return GraphPortOrder.copy(res);
    }

    public static Map<String, GraphValue> defaults(){
        return Map.of("angle", GraphValue.number(0), "axis", MathGraphValues.vector(DEFAULT_AXIS),
                "angles", GraphValue.map(Map.of()), "euler", MathGraphValues.euler(Vector3.ZERO),
                "tait_bryan", MathGraphValues.taitBryan(Vector3.ZERO), "quaternion", MathGraphValues.quaternion(Quaternion.IDENTITY));
    }

    // Invalid values return typed empty outputs and Valid = false
    public static Map<String, GraphValue> execute(Format from, Format to, Map<String, GraphValue> supplied){
        Map<String, GraphValue> in = new LinkedHashMap<>(defaults());
        in.putAll(supplied);
        try{
            for(var port : inputs(from).entrySet()){
                GraphValue val = in.get(port.getKey());
                if(val == null || !port.getValue().equals(val.type())) throw new IllegalArgumentException("Incorrect angle input type");
            }
            Map<String, GraphValue> res = new LinkedHashMap<>();
            if(from.scalar() && to.scalar()){
                res.put("angle", MathGraphValues.number(to.unit.fromRadians(from.unit.toRadians(number(in.get("angle"))))));
                res.put("axis", in.get("axis"));
                res.put("angles", convertMap(in.get("angles"), from.unit, to.unit));
            }else{
                Quaternion rotation = rotation(from, in);
                if(to.scalar() || to == Format.AXIS_ANGLE){
                    var axisAngle = rotation.axisAngle();
                    res.put("angle", MathGraphValues.number(to.scalar() ? to.unit.fromRadians(axisAngle.radians()) : axisAngle.radians()));
                    res.put("axis", MathGraphValues.vector(axisAngle.axis()));
                    if(to.scalar()) res.put("angles", GraphValue.map(Map.of()));
                }else if(to == Format.QUATERNION){
                    res.put("quaternion", MathGraphValues.quaternion(rotation));
                }else{
                    Vector3 angles = to == Format.EULER ? RotationMath.quaternionToEulerZxz(rotation) : RotationMath.quaternionToTaitBryanXyz(rotation);
                    res.put(to.id(), to == Format.EULER ? MathGraphValues.euler(angles) : MathGraphValues.taitBryan(angles));
                    List<String> keys = to == Format.EULER ? List.of("alpha", "beta", "gamma") : List.of("roll", "pitch", "yaw");
                    res.put(keys.get(0), MathGraphValues.number(angles.x()));
                    res.put(keys.get(1), MathGraphValues.number(angles.y()));
                    res.put(keys.get(2), MathGraphValues.number(angles.z()));
                }
            }
            res.put("valid", GraphValue.bool(true));
            return GraphPortOrder.copy(res);
        }catch(IllegalArgumentException err){
            Map<String, GraphValue> res = new LinkedHashMap<>();
            outputs(to).forEach((key, type) -> res.put(key, type.equals("map") ? GraphValue.map(Map.of())
                    : type.equals("boolean") ? GraphValue.bool(false) : GraphValue.number(0)));
            return GraphPortOrder.copy(res);
        }
    }

    // Keep the old converters registered for saved graphs while hosts hide them from their palettes
    public static boolean supersedes(String type){
        return type != null && (List.of("quaternion_to_euler", "quaternion_to_tait_bryan", "euler_to_quaternion",
                "tait_bryan_to_quaternion", "euler_to_tait_bryan", "tait_bryan_to_euler").contains(type)
                || List.of("degrees_to_radians", "radians_to_degrees", "quaternion_from_axis_angle", "quaternion_to_axis_angle")
                .contains(type.substring(type.indexOf(':') + 1)));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Quaternion rotation(Format from, Map<String, GraphValue> in){
        if(from.scalar() || from == Format.AXIS_ANGLE){
            Vector3 axis = MathGraphValues.vector(in.get("axis"));
            double angle = number(in.get("angle"));
            if(!axis.isFinite() || axis.magnitude() == 0 && angle != 0) throw new IllegalArgumentException("A nonzero angle requires an axis");
            return Quaternion.fromAxisAngle(axis, from.scalar() ? from.unit.toRadians(angle) : angle);
        }
        if(from == Format.QUATERNION){
            Quaternion q = MathGraphValues.quaternion(in.get("quaternion"));
            if(!Double.isFinite(q.x()) || !Double.isFinite(q.y()) || !Double.isFinite(q.z()) || !Double.isFinite(q.w())
                    || q.x() == 0 && q.y() == 0 && q.z() == 0 && q.w() == 0) throw new IllegalArgumentException("A rotation requires a nonzero finite quaternion");
            return q.normalized();
        }
        Vector3 angles = from == Format.EULER ? MathGraphValues.euler(in.get("euler")) : MathGraphValues.taitBryan(in.get("tait_bryan"));
        if(!angles.isFinite()) throw new IllegalArgumentException("Angles must be finite");
        return from == Format.EULER ? RotationMath.eulerZxzToQuaternion(angles) : RotationMath.taitBryanXyzToQuaternion(angles);
    }

    private static double number(GraphValue val){
        MathGraphValues.number(val.asNumber());
        return val.asNumber();
    }

    private static GraphValue convertMap(GraphValue val, AngleUnit from, AngleUnit to){
        Map<String, Object> res = new LinkedHashMap<>();
        if(!(val.value() instanceof Map<?, ?> map)) throw new IllegalArgumentException("An angles map is required");
        map.forEach((key, entry) -> res.put(String.valueOf(key), convertEntry(entry, from, to)));
        return GraphValue.map(res);
    }

    private static GraphValue convertEntry(Object raw, AngleUnit from, AngleUnit to){
        if(raw instanceof Number num) return MathGraphValues.number(to.fromRadians(from.toRadians(num.doubleValue())));
        GraphValue val = GraphValue.of(raw);
        return switch(val.type()){
            case "number" -> MathGraphValues.number(to.fromRadians(from.toRadians(number(val))));
            case "map" -> convertMap(val, from, to);
            case "list" -> GraphValue.list(val.entries().stream().map(entry -> convertEntry(entry, from, to)).toList());
            default -> val;
        };
    }
}
