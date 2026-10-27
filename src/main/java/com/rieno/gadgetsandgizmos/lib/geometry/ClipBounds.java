package com.rieno.gadgetsandgizmos.lib.geometry;

import org.joml.Matrix4fc;

// Reject transformed bounds only when every corner lies outside the same clip plane
public final class ClipBounds{
    private ClipBounds(){}

    // Supply projection times view times model, with bounds expressed in the model's coordinates
    public static boolean intersects(Matrix4fc matrix, double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ){
        if(!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) return true;
        int outside = 63;
        for(int idx = 0; idx < 8; idx++){
            double x = (idx & 1) == 0 ? minX : maxX;
            double y = (idx & 2) == 0 ? minY : maxY;
            double z = (idx & 4) == 0 ? minZ : maxZ;
            double cx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
            double cy = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
            double cz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();
            double cw = matrix.m03() * x + matrix.m13() * y + matrix.m23() * z + matrix.m33();
            if(!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz) || !Double.isFinite(cw)) return true;
            double eps = 0.0001D + Math.abs(cw) * 0.00001D;
            int mask = 0;
            if(cx < -cw - eps) mask |= 1;
            if(cx > cw + eps) mask |= 2;
            if(cy < -cw - eps) mask |= 4;
            if(cy > cw + eps) mask |= 8;
            if(cz < -cw - eps) mask |= 16;
            if(cz > cw + eps) mask |= 32;
            outside &= mask;
            if(outside == 0) return true;
        }
        return false;
    }
}
