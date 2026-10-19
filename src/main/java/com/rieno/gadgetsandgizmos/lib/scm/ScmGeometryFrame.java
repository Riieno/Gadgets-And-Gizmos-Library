package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Retain a rigid frame as an origin and three orthonormal axes
public record ScmGeometryFrame(Vec3 origin, Vec3 xAxis, Vec3 yAxis, Vec3 zAxis){
    // Keep the orientation while remapping the origin into another saved coordinate space
    public ScmGeometryFrame withOrigin(Vec3 pos){
        return new ScmGeometryFrame(pos, xAxis, yAxis, zAxis);
    }

    // Store a calibration frame without exposing a mutable pose object
    public CompoundTag write(){
        CompoundTag tag = new CompoundTag();
        Vec3[] vectors = {origin, xAxis, yAxis, zAxis};
        for(int idx = 0; idx < vectors.length; idx++){
            tag.putDouble("X" + idx, vectors[idx].x);
            tag.putDouble("Y" + idx, vectors[idx].y);
            tag.putDouble("Z" + idx, vectors[idx].z);
        }
        return tag;
    }

    // Ignore incomplete or invalid legacy frame data
    public static ScmGeometryFrame read(CompoundTag tag){
        Vec3[] vectors = new Vec3[4];
        for(int idx = 0; idx < vectors.length; idx++){
            if(!tag.contains("X" + idx, Tag.TAG_ANY_NUMERIC) || !tag.contains("Y" + idx, Tag.TAG_ANY_NUMERIC)
                    || !tag.contains("Z" + idx, Tag.TAG_ANY_NUMERIC)) return null;
            Vec3 val = new Vec3(tag.getDouble("X" + idx), tag.getDouble("Y" + idx), tag.getDouble("Z" + idx));
            if(!Double.isFinite(val.x) || !Double.isFinite(val.y) || !Double.isFinite(val.z)) return null;
            vectors[idx] = val;
        }
        for(int idx = 1; idx < 4; idx++){
            if(Math.abs(vectors[idx].lengthSqr() - 1.0D) > 1.0E-4D) return null;
        }
        if(Math.abs(vectors[1].dot(vectors[2])) > 1.0E-4D || Math.abs(vectors[1].dot(vectors[3])) > 1.0E-4D
                || Math.abs(vectors[2].dot(vectors[3])) > 1.0E-4D) return null;
        return new ScmGeometryFrame(vectors[0], vectors[1], vectors[2], vectors[3]);
    }

    // Move a calibrated point with its host frame
    public Vec3 positionIn(ScmGeometryFrame target, Vec3 pos){
        return target.origin.add(directionIn(target, pos.subtract(origin)));
    }

    // Rotate a calibrated direction with its host frame
    public Vec3 directionIn(ScmGeometryFrame target, Vec3 dir){
        return target.xAxis.scale(dir.dot(xAxis))
                .add(target.yAxis.scale(dir.dot(yAxis)))
                .add(target.zAxis.scale(dir.dot(zAxis)));
    }
}
