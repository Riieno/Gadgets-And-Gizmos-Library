package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// Draw a transformed rectangular area with translucent faces and solid edges
public final class WorldAreaOverlayRenderer{
    private static final int[][] FACES = {
            {0, 1, 5, 4}, {3, 2, 6, 7}, {0, 3, 7, 4},
            {1, 2, 6, 5}, {0, 1, 2, 3}, {4, 5, 6, 7}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final Direction[] FACE_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST,
            Direction.EAST, Direction.DOWN, Direction.UP
    };

    private WorldAreaOverlayRenderer(){}

    // Find the nearest wall intersected by a view segment, including transformed areas
    public static Hit hitFace(Vec3[] corners, Vec3 eye, Vec3 end){
        if(corners == null || corners.length != 8 || eye == null || end == null) return null;
        Vec3 ray = end.subtract(eye);
        Hit closest = null;
        for(int idx = 0; idx < FACES.length; idx++){
            int[] face = FACES[idx];
            Vec3 origin = corners[face[0]];
            Vec3 u = corners[face[1]].subtract(origin);
            Vec3 v = corners[face[3]].subtract(origin);
            Vec3 normal = u.cross(v);
            double denominator = normal.dot(ray);
            if(Math.abs(denominator) < 1.0E-8D) continue;
            double fraction = normal.dot(origin.subtract(eye)) / denominator;
            if(fraction < 0.0D || fraction > 1.0D) continue;
            Vec3 offset = eye.add(ray.scale(fraction)).subtract(origin);
            double alongU = offset.dot(u) / u.lengthSqr();
            double alongV = offset.dot(v) / v.lengthSqr();
            if(alongU < -0.001D || alongU > 1.001D || alongV < -0.001D || alongV > 1.001D) continue;
            double distance = ray.length() * fraction;
            if(closest == null || distance < closest.distance()) closest = new Hit(FACE_DIRECTIONS[idx], distance);
        }
        return closest;
    }

    public record Hit(Direction face, double distance){}

    // Draw all six faces on both sides
    public static void walls(PoseStack pose, VertexConsumer consumer, Vec3[] corners,
                             float red, float green, float blue, float alpha){
        if(corners == null || corners.length != 8) return;
        Matrix4f matrix = pose.last().pose();
        for(int[] face : FACES){
            Vec3 normal = corners[face[1]].subtract(corners[face[0]])
                    .cross(corners[face[2]].subtract(corners[face[0]])).normalize();
            for(int direction = 0; direction < 2; direction++){
                for(int idx = 0; idx < 4; idx++){
                    Vec3 vertex = corners[face[direction == 0 ? idx : 3 - idx]];
                    consumer.addVertex(matrix, (float)vertex.x, (float)vertex.y, (float)vertex.z)
                            .setColor(red, green, blue, alpha)
                            .setUv(idx == 1 || idx == 2 ? 1.0F : 0.0F, idx >= 2 ? 1.0F : 0.0F)
                            .setLight(0xF000F0).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setNormal((float)(direction == 0 ? normal.x : -normal.x),
                                    (float)(direction == 0 ? normal.y : -normal.y),
                                    (float)(direction == 0 ? normal.z : -normal.z));
                }
            }
        }
    }

    // Draw the twelve solid borders
    public static void edges(PoseStack pose, VertexConsumer consumer, Vec3[] corners,
                             float red, float green, float blue){
        if(corners == null || corners.length != 8) return;
        Matrix4f matrix = pose.last().pose();
        for(int[] edge : EDGES){
            Vec3 from = corners[edge[0]];
            Vec3 to = corners[edge[1]];
            Vec3 normal = to.subtract(from).normalize();
            consumer.addVertex(matrix, (float)from.x, (float)from.y, (float)from.z)
                    .setColor(red, green, blue, 1.0F)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
            consumer.addVertex(matrix, (float)to.x, (float)to.y, (float)to.z)
                    .setColor(red, green, blue, 1.0F)
                    .setNormal((float)normal.x, (float)normal.y, (float)normal.z);
        }
    }
}
