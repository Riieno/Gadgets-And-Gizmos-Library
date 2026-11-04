package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

// Reuse exact shape unions without retaining block entities or world state
public final class VoxelShapeCache{
    private final int capacity;
    private final Map<Object, VoxelShape> shapes = new ConcurrentHashMap<>();

    public VoxelShapeCache(int capacity){
        if(capacity < 1) throw new IllegalArgumentException("Shape capacity must be positive");
        this.capacity = capacity;
    }

    // Preserve the input shapes and their union order
    public VoxelShape union(VoxelShape first, VoxelShape second){
        var key = new UnionKey(Objects.requireNonNull(first), Objects.requireNonNull(second));
        VoxelShape prev = shapes.get(key);
        if(prev != null) return prev;
        return retain(key, Shapes.or(first, second));
    }

    // Append immutable boxes in the same order as an uncached shape query
    public VoxelShape append(VoxelShape base, List<AABB> boxes){
        if(boxes.isEmpty()) return base;
        var key = new BoxesKey(Objects.requireNonNull(base), List.copyOf(boxes));
        VoxelShape prev = shapes.get(key);
        if(prev != null) return prev;
        VoxelShape res = base;
        for(AABB box : key.boxes()) res = Shapes.or(res, Shapes.create(box));
        return retain(key, res);
    }

    // Precompute every connection mask while keeping the supplied component order
    public static IntFunction<VoxelShape> combinations(VoxelShape base, VoxelShape... parts){
        Objects.requireNonNull(base);
        if(parts.length > 12) throw new IllegalArgumentException("Too many shape components");
        VoxelShape[] shapes = new VoxelShape[1 << parts.length];
        for(int mask = 0; mask < shapes.length; mask++){
            VoxelShape res = base;
            for(int idx = 0; idx < parts.length; idx++){
                if((mask & 1 << idx) != 0) res = Shapes.or(res, Objects.requireNonNull(parts[idx]));
            }
            shapes[mask] = res;
        }
        return mask -> shapes[mask];
    }

    // Bound retained geometry across world changes and dynamic block shapes
    private VoxelShape retain(Object key, VoxelShape shape){
        synchronized(shapes){
            if(shapes.size() >= capacity) shapes.clear();
            VoxelShape prev = shapes.putIfAbsent(key, shape);
            return prev == null ? shape : prev;
        }
    }

    private record UnionKey(VoxelShape first, VoxelShape second){
        @Override public boolean equals(Object val){
            return val instanceof UnionKey other && first == other.first && second == other.second;
        }
        @Override public int hashCode(){
            return 31 * System.identityHashCode(first) + System.identityHashCode(second);
        }
    }
    private record BoxesKey(VoxelShape base, List<AABB> boxes){
        @Override public boolean equals(Object val){
            return val instanceof BoxesKey other && base == other.base && boxes.equals(other.boxes);
        }
        @Override public int hashCode(){ return 31 * System.identityHashCode(base) + boxes.hashCode(); }
    }
}
