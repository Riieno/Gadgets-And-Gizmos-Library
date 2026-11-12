package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.control.math.AngleUnit;
import com.rieno.gadgetsandgizmos.lib.control.math.Matrix3;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Share math map formats between graph hosts without depending on NBT or addon classes
public final class MathGraphValues{
    private MathGraphValues(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static Vector3 vector(GraphValue val){
        return new Vector3(component(val, "x", 0), component(val, "y", 0), component(val, "z", 0));
    }

    public static Quaternion quaternion(GraphValue val){
        return new Quaternion(component(val, "x", 0), component(val, "y", 0),
                component(val, "z", 0), component(val, "w", 1));
    }

    public static Matrix3 matrix(GraphValue val){
        return new Matrix3(component(val, "m00", 0), component(val, "m01", 0), component(val, "m02", 0),
                component(val, "m10", 0), component(val, "m11", 0), component(val, "m12", 0),
                component(val, "m20", 0), component(val, "m21", 0), component(val, "m22", 0));
    }

    public static GraphValue vector(Vector3 val){
        return GraphValue.map(GraphPortOrder.entries(Map.entry("x", number(val.x())),
                Map.entry("y", number(val.y())), Map.entry("z", number(val.z()))));
    }

    // Preserve raw components for algebra; normalization belongs to rotation operations
    public static GraphValue quaternion(Quaternion val){
        return GraphValue.map(GraphPortOrder.entries(Map.entry("x", number(val.x())), Map.entry("y", number(val.y())),
                Map.entry("z", number(val.z())), Map.entry("w", number(val.w()))));
    }

    public static GraphValue matrix(Matrix3 val){
        Map<String, GraphValue> res = new LinkedHashMap<>();
        for(int idx = 0; idx < 3; idx++){
            Vector3 row = val.row(idx);
            res.put("m" + idx + "0", number(row.x()));
            res.put("m" + idx + "1", number(row.y()));
            res.put("m" + idx + "2", number(row.z()));
        }
        return GraphValue.map(res);
    }

    // Keep XYZ aliases so existing graphs can read saved angle maps
    public static GraphValue euler(Vector3 val){
        return rotationAngles(angles(val, "alpha", "beta", "gamma"), AngleUnit.RADIANS, "zyz");
    }

    public static GraphValue taitBryan(Vector3 val){
        return rotationAngles(angles(val, "roll", "pitch", "yaw"), AngleUnit.RADIANS, "xyz");
    }

    // Store YXZ Tait-Bryan yaw, pitch and roll in radians with physical XYZ aliases
    public static GraphValue yawPitchRoll(Vector3 val){
        return rotationAngles(GraphValue.map(GraphPortOrder.entries(Map.entry("x", number(val.y())), Map.entry("y", number(val.x())),
                Map.entry("z", number(val.z())), Map.entry("yaw", number(val.x())),
                Map.entry("pitch", number(val.y())), Map.entry("roll", number(val.z())))), AngleUnit.RADIANS, "yxz");
    }

    public static Vector3 yawPitchRoll(GraphValue val){
        return new Vector3(component(val, "yaw", component(val, "y", 0)),
                component(val, "pitch", component(val, "x", 0)), component(val, "roll", component(val, "z", 0)));
    }

    // Attach a unit to an angle map without changing its numeric components
    public static GraphValue rotationAngles(GraphValue val, AngleUnit unit){
        Map<String, Object> res = new LinkedHashMap<>();
        if(val != null && val.value() instanceof Map<?, ?> map) map.forEach((key, entry) -> res.put(String.valueOf(key), entry));
        res.put("unit", GraphValue.string(unit.id()));
        return GraphValue.map(res);
    }

    // Identify a rotation's representation, axis convention and angle unit without changing its components
    public static GraphValue rotationAngles(GraphValue val, AngleUnit unit, String convention){
        Map<String, Object> res = new LinkedHashMap<>();
        ((Map<?, ?>)rotationAngles(val, unit).value()).forEach((key, entry) -> res.put(String.valueOf(key), entry));
        res.put("convention", GraphValue.string(convention));
        res.put("representation", GraphValue.string(representation(convention)));
        return GraphValue.map(res);
    }

    // Decode rotation maps using explicit metadata and the host's defaults for untagged values
    public static Quaternion rotation(GraphValue val, String fallbackConvention, AngleUnit fallbackUnit){
        if(val == null || !"map".equals(val.type()) || !(val.value() instanceof Map<?, ?> map)) throw new IllegalArgumentException("A rotation map is required");
        String convention = text(val, "convention");
        if(convention.isEmpty()) convention = "zyz".equals(fallbackConvention) && (map.containsKey("yaw") || map.containsKey("YAW")) ? "yxz" : fallbackConvention;
        String kind = representation(convention), supplied = text(val, "representation");
        if(!supplied.isEmpty() && !kind.equals(supplied)) throw new IllegalArgumentException("The rotation representation does not match its convention");
        for(String key : List.of("x", "y", "z", "alpha", "beta", "gamma", "roll", "pitch", "yaw")){
            Object raw = map.containsKey(key) ? map.get(key) : map.get(key.toUpperCase(Locale.ROOT));
            if(raw != null && !"number".equals(GraphValue.of(raw).type())) throw new IllegalArgumentException("Rotation components must be numbers");
        }
        String unitId = text(val, "unit");
        if(!unitId.isEmpty() && !AngleUnit.optionIds().contains(unitId)) throw new IllegalArgumentException("Unknown rotation angle unit");
        AngleUnit unit = unitId.isEmpty() ? fallbackUnit : AngleUnit.fromId(unitId);
        Vector3 angles = switch(convention){
            case "zyz", "zxz" -> euler(val);
            case "xyz" -> taitBryan(val);
            default -> yawPitchRoll(val);
        };
        if(!angles.isFinite()) throw new IllegalArgumentException("Rotation components must be finite");
        angles = new Vector3(unit.toRadians(angles.x()), unit.toRadians(angles.y()), unit.toRadians(angles.z()));
        return switch(convention){
            case "zyz" -> RotationMath.eulerZyzToQuaternion(angles);
            case "zxz" -> RotationMath.eulerZxzToQuaternion(angles);
            case "xyz" -> RotationMath.taitBryanXyzToQuaternion(angles);
            default -> RotationMath.yawPitchRollToQuaternion(angles);
        };
    }

    public static Vector3 euler(GraphValue val){
        return new Vector3(component(val, "alpha", component(val, "x", 0)),
                component(val, "beta", component(val, "y", 0)), component(val, "gamma", component(val, "z", 0)));
    }

    public static Vector3 taitBryan(GraphValue val){
        return new Vector3(component(val, "roll", component(val, "x", 0)),
                component(val, "pitch", component(val, "y", 0)), component(val, "yaw", component(val, "z", 0)));
    }

    // Convert one angle without wrapping or clamping its signed value
    public static GraphValue convertAngle(double angle, boolean toRadians){
        number(angle);
        return number(toRadians ? Math.toRadians(angle) : Math.toDegrees(angle));
    }

    public static GraphValue convertAngles(GraphValue val, boolean toRadians){
        if(val == null || !(val.value() instanceof Map<?, ?> map)) return GraphValue.map(Map.of());
        Map<String, Object> res = new LinkedHashMap<>();
        map.forEach((key, entry) -> res.put(String.valueOf(key), convert(entry, toRadians)));
        if(map.containsKey("unit")) res.put("unit", GraphValue.string((toRadians ? AngleUnit.RADIANS : AngleUnit.DEGREES).id()));
        return GraphValue.map(res);
    }

    public static double component(GraphValue val, String key, double fallback){
        if(val == null || !(val.value() instanceof Map<?, ?> map)) return fallback;
        Object raw = map.containsKey(key) ? map.get(key) : map.get(key.toUpperCase(Locale.ROOT));
        return raw == null ? fallback : numeric(raw);
    }

    public static GraphValue number(double val){
        if(!Double.isFinite(val)) throw new IllegalArgumentException("Math values must be finite");
        return GraphValue.number(val);
    }

    private static GraphValue angles(Vector3 val, String x, String y, String z){
        return GraphValue.map(GraphPortOrder.entries(Map.entry("x", number(val.x())), Map.entry("y", number(val.y())),
                Map.entry("z", number(val.z())), Map.entry(x, number(val.x())), Map.entry(y, number(val.y())), Map.entry(z, number(val.z()))));
    }

    private static String representation(String convention){
        return switch(convention){
            case "zyz", "zxz" -> "euler";
            case "xyz", "yxz" -> "tait_bryan";
            default -> throw new IllegalArgumentException("Unknown rotation convention");
        };
    }

    private static String text(GraphValue val, String key){
        if(!(val.value() instanceof Map<?, ?> map)) return "";
        Object raw = map.containsKey(key) ? map.get(key) : map.get(key.toUpperCase(Locale.ROOT));
        return raw == null ? "" : GraphValue.of(raw).asString().strip().toLowerCase(Locale.ROOT);
    }

    private static double numeric(Object raw){
        double val = raw instanceof GraphValue graph ? graph.asNumber()
                : raw instanceof Number num ? num.doubleValue() : 0;
        number(val);
        return val;
    }

    private static Object convert(Object raw, boolean toRadians){
        if(raw instanceof GraphValue val){
            if("number".equals(val.type())) return convertAngle(val.asNumber(), toRadians);
            if("map".equals(val.type())) return convertAngles(val, toRadians);
            if("list".equals(val.type()) && val.value() instanceof List<?> list){
                return GraphValue.list(list.stream().map(entry -> convert(entry, toRadians)).toList());
            }
            return val;
        }
        if(raw instanceof Number num) return convertAngle(num.doubleValue(), toRadians);
        if(raw instanceof Map<?, ?> map) return convertAngles(GraphValue.map(map), toRadians);
        if(raw instanceof List<?> list) return GraphValue.list(list.stream().map(entry -> convert(entry, toRadians)).toList());
        return raw;
    }
}
