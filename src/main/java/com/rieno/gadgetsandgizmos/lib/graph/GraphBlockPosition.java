package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.core.BlockPos;

import java.util.Map;

// Encode optional block coordinates without turning missing fields into the origin
public final class GraphBlockPosition{
    private GraphBlockPosition(){}

    public static GraphValue value(BlockPos pos){
        return GraphValue.map(pos == null ? Map.of() : Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
    }

    public static BlockPos read(GraphValue val){
        if(val == null || !(val.value() instanceof Map<?, ?> map)) return null;
        for(String axis : new String[]{"x", "y", "z"}){
            Object coordinate = map.get(axis);
            if(coordinate instanceof GraphValue graphVal) coordinate = graphVal.value();
            if(!(coordinate instanceof Number num) || !Double.isFinite(num.doubleValue())
                    || num.doubleValue() < Integer.MIN_VALUE || num.doubleValue() > Integer.MAX_VALUE) return null;
        }
        return BlockPos.containing(val.member("x").asNumber(), val.member("y").asNumber(), val.member("z").asNumber());
    }
}
