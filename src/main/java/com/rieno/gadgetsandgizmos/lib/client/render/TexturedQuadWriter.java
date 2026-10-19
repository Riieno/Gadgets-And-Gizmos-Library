package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Submit complete quad vertices through Minecraft's optimized consumer API
public final class TexturedQuadWriter{
    private TexturedQuadWriter(){}

    // Preserve both windings, lighting and UVs while transforming each position once
    public static void drawDoubleSided(PoseStack.Pose pose, VertexConsumer consumer,
                                      Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                      float minU, float maxU, float minV, float maxV,
                                      int lightFrom, int lightTo, int overlay){
        Vec3 normal = b.subtract(a).cross(d.subtract(a)).normalize();
        if(normal.lengthSqr() <= 1.0E-10D) normal = new Vec3(0.0D, 1.0D, 0.0D);
        var scratch = new Vector3f();
        pose.transformNormal((float)normal.x, (float)normal.y, (float)normal.z, scratch);
        float nx = scratch.x, ny = scratch.y, nz = scratch.z;
        pose.transformNormal((float)-normal.x, (float)-normal.y, (float)-normal.z, scratch);
        float bnx = scratch.x, bny = scratch.y, bnz = scratch.z;
        var matrix = pose.pose();
        matrix.transformPosition((float)a.x, (float)a.y, (float)a.z, scratch);
        float ax = scratch.x, ay = scratch.y, az = scratch.z;
        matrix.transformPosition((float)b.x, (float)b.y, (float)b.z, scratch);
        float bx = scratch.x, by = scratch.y, bz = scratch.z;
        matrix.transformPosition((float)c.x, (float)c.y, (float)c.z, scratch);
        float cx = scratch.x, cy = scratch.y, cz = scratch.z;
        matrix.transformPosition((float)d.x, (float)d.y, (float)d.z, scratch);
        float dx = scratch.x, dy = scratch.y, dz = scratch.z;

        consumer.addVertex(ax, ay, az, -1, minU, minV, overlay, lightFrom, nx, ny, nz);
        consumer.addVertex(bx, by, bz, -1, maxU, minV, overlay, lightFrom, nx, ny, nz);
        consumer.addVertex(cx, cy, cz, -1, maxU, maxV, overlay, lightTo, nx, ny, nz);
        consumer.addVertex(dx, dy, dz, -1, minU, maxV, overlay, lightTo, nx, ny, nz);
        consumer.addVertex(dx, dy, dz, -1, minU, maxV, overlay, lightTo, bnx, bny, bnz);
        consumer.addVertex(cx, cy, cz, -1, maxU, maxV, overlay, lightTo, bnx, bny, bnz);
        consumer.addVertex(bx, by, bz, -1, maxU, minV, overlay, lightFrom, bnx, bny, bnz);
        consumer.addVertex(ax, ay, az, -1, minU, minV, overlay, lightFrom, bnx, bny, bnz);
    }
}
