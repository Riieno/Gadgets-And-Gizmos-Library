package com.rieno.gadgetsandgizmos.lib.client.render;

import org.joml.Matrix4fc;

// Estimate the largest projected block edge using the nearest depth of a chunk
public final class ProjectionDetail{
    private ProjectionDetail(){}

    public static double blockPixels(Matrix4fc clip, double minY, double maxY, int width, int height){
        double y = (minY + maxY) * .5;
        double w = clip.m03() * 8 + clip.m13() * y + clip.m23() * 8 + clip.m33();
        double nearest = w - Math.abs(clip.m03()) * 8 - Math.abs(clip.m13()) * (maxY - minY) * .5 - Math.abs(clip.m23()) * 8;
        if(nearest <= .01 || width <= 0 || height <= 0) return Double.POSITIVE_INFINITY;
        double x = clip.m00() * 8 + clip.m10() * y + clip.m20() * 8 + clip.m30();
        double cy = clip.m01() * 8 + clip.m11() * y + clip.m21() * 8 + clip.m31();
        double res = 0;
        for(int axis = 0; axis < 3; axis++){
            double dx = axis == 0 ? clip.m00() : axis == 1 ? clip.m10() : clip.m20();
            double dy = axis == 0 ? clip.m01() : axis == 1 ? clip.m11() : clip.m21();
            double dw = axis == 0 ? clip.m03() : axis == 1 ? clip.m13() : clip.m23();
            res = Math.max(res, Math.hypot((dx - x / w * dw) * width, (dy - cy / w * dw) * height) / (2 * nearest));
        }
        return Double.isFinite(res) ? res : Double.POSITIVE_INFINITY;
    }
}
