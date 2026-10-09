package com.rieno.gadgetsandgizmos.lib.graph.math;

import com.rieno.gadgetsandgizmos.lib.control.math.Matrix3;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

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
        return GraphValue.map(Map.of("x", number(val.x()), "y", number(val.y()), "z", number(val.z())));
    }

    // Preserve raw components for algebra; normalization belongs to rotation operations
    public static GraphValue quaternion(Quaternion val){
        return GraphValue.map(Map.of("x", number(val.x()), "y", number(val.y()),
                "z", number(val.z()), "w", number(val.w())));
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
        return angles(val, "alpha", "beta", "gamma");
    }

    public static GraphValue taitBryan(Vector3 val){
        return angles(val, "roll", "pitch", "yaw");
    }

    public static Vector3 euler(GraphValue val){
        return new Vector3(component(val, "alpha", component(val, "x", 0)),
                component(val, "beta", component(val, "y", 0)), component(val, "gamma", component(val, "z", 0)));
    }

    public static Vector3 taitBryan(GraphValue val){
        return new Vector3(component(val, "roll", component(val, "x", 0)),
                component(val, "pitch", component(val, "y", 0)), component(val, "yaw", component(val, "z", 0)));
    }

    public static GraphValue convertAngles(GraphValue val, boolean toRadians){
        if(val == null || !(val.value() instanceof Map<?, ?> map)) return GraphValue.map(Map.of());
        Map<String, Object> res = new LinkedHashMap<>();
        map.forEach((key, entry) -> res.put(String.valueOf(key), convert(entry, toRadians)));
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
        return GraphValue.map(Map.of("x", number(val.x()), "y", number(val.y()), "z", number(val.z()),
                x, number(val.x()), y, number(val.y()), z, number(val.z())));
    }

    private static double numeric(Object raw){
        double val = raw instanceof GraphValue graph ? graph.asNumber()
                : raw instanceof Number num ? num.doubleValue() : 0;
        number(val);
        return val;
    }

    private static Object convert(Object raw, boolean toRadians){
        if(raw instanceof GraphValue val){
            if("number".equals(val.type())) return number(toRadians ? Math.toRadians(val.asNumber()) : Math.toDegrees(val.asNumber()));
            if("map".equals(val.type())) return convertAngles(val, toRadians);
            if("list".equals(val.type()) && val.value() instanceof List<?> list){
                return GraphValue.list(list.stream().map(entry -> convert(entry, toRadians)).toList());
            }
            return val;
        }
        if(raw instanceof Number num) return number(toRadians ? Math.toRadians(num.doubleValue()) : Math.toDegrees(num.doubleValue()));
        if(raw instanceof Map<?, ?> map) return convertAngles(GraphValue.map(map), toRadians);
        if(raw instanceof List<?> list) return GraphValue.list(list.stream().map(entry -> convert(entry, toRadians)).toList());
        return raw;
    }
}
