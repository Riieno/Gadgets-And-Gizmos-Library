package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

// Keep physical side aliases separate from the host's internal peripherals
public final class ScriptPeripheralSides{
    public record Neighbour(BlockEntity target, Direction face, String alias){}
    private ScriptPeripheralSides(){}

    public static String side(Direction front, Direction neighbour){
        if(front.getAxis().isVertical()) throw new IllegalArgumentException("Front must be horizontal");
        if(neighbour == Direction.UP) return "top";
        if(neighbour == Direction.DOWN) return "bottom";
        if(neighbour == front) return "front";
        if(neighbour == front.getOpposite()) return "back";
        return neighbour == front.getCounterClockWise() ? "left" : "right";
    }

    public static String alias(Direction front, Direction neighbour){ return "side:" + side(front, neighbour); }

    // Inspect only loaded neighbours and address the face touching the host
    public static List<Neighbour> adjacent(Level level, BlockPos pos, Direction front){
        List<Neighbour> res = new ArrayList<>();
        for(var dir : Direction.values()){
            BlockPos next = pos.relative(dir);
            if(!level.hasChunkAt(next)) continue;
            var target = level.getBlockEntity(next);
            if(target != null && !target.isRemoved()) res.add(new Neighbour(target, dir.getOpposite(), alias(front, dir)));
        }
        return List.copyOf(res);
    }
}
