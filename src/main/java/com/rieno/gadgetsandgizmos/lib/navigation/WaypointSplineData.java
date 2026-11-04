package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Share detached waypoint NBT between route drafts and saved movement state
public final class WaypointSplineData{
    private WaypointSplineData(){}

    public static CompoundTag vector(Vec3 val){
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", val.x); tag.putDouble("Y", val.y); tag.putDouble("Z", val.z);
        return tag;
    }

    public static Vec3 vector(CompoundTag tag){
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    public static ListTag points(List<Vec3> points){
        ListTag tag = new ListTag();
        points.forEach(point -> tag.add(vector(point)));
        return tag;
    }

    // Bound caller-provided lists before allocating their detached controls
    public static List<Vec3> readPoints(ListTag tag, int maximum){
        List<Vec3> points = new ArrayList<>();
        for(int idx = 0; idx < Math.min(tag.size(), Math.max(0, maximum)); idx++) points.add(vector(tag.getCompound(idx)));
        return List.copyOf(points);
    }
}
