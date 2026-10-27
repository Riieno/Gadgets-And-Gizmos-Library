package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

// Tile NORTH-facing model segments by their baked size and centre them on a spline
public final class SplineModelRenderer{
    private static final Map<BakedModel, AABB> BOUNDS = new WeakHashMap<>();
    private SplineModelRenderer(){}

    public static void render(PoseStack poses, Vec3 camera, MultiBufferSource buffers, WaypointSpline spline,
            PartialModel model, Vec3 up, int color){
        render(poses, camera, buffers, spline, model, up, color, RenderType.cutoutMipped());
    }

    // Accept an alternate material without changing the model geometry or placement
    public static void render(PoseStack poses, Vec3 camera, MultiBufferSource buffers, WaypointSpline spline,
            PartialModel model, Vec3 up, int color, RenderType material){
        if(spline.isEmpty() || model.get() == null) return;
        AABB bounds = bounds(model.get());
        var points = spline.sample(Math.max(1.0D / 64, bounds.getZsize()), 16384);
        for(int idx = 1; idx < points.size(); idx++){
            Vec3 start = points.get(idx - 1), end = points.get(idx);
            renderModel(poses, camera, buffers, model, start.add(end).scale(0.5D), end.subtract(start), up,
                    end.distanceTo(start) / Math.max(1.0D / 64, bounds.getZsize()), color, material);
        }
    }

    public static void renderPoint(PoseStack poses, Vec3 camera, MultiBufferSource buffers, PartialModel model,
            Vec3 position, Vec3 forward, Vec3 up, int color){
        if(model.get() != null) renderModel(poses, camera, buffers, model, position, forward, up, 1, color, RenderType.cutoutMipped());
    }

    // Resource reloads supply a new baked-model identity, invalidating the cached dimensions
    public static AABB bounds(BakedModel model){
        return BOUNDS.computeIfAbsent(model, val -> {
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for(int face = -1; face < Direction.values().length; face++){
                var quads = val.getQuads(null, face < 0 ? null : Direction.values()[face], RandomSource.create(0));
                for(var quad : quads){
                    int[] data = quad.getVertices();
                    int stride = data.length / 4;
                    for(int idx = 0; idx < 4; idx++){
                        float x = Float.intBitsToFloat(data[idx * stride]);
                        float y = Float.intBitsToFloat(data[idx * stride + 1]);
                        float z = Float.intBitsToFloat(data[idx * stride + 2]);
                        minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                    }
                }
            }
            return Double.isFinite(minX) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : new AABB(0, 0, 0, 1, 1, 1);
        });
    }

    private static void renderModel(PoseStack poses, Vec3 camera, MultiBufferSource buffers, PartialModel model,
            Vec3 position, Vec3 forward, Vec3 up, double lengthScale, int color, RenderType material){
        if(forward.lengthSqr() < 1.0E-12D) return;
        Vec3 direction = forward.normalize(), right = direction.cross(up).normalize();
        if(right.lengthSqr() < 0.5D) right = direction.cross(Math.abs(direction.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 vertical = right.cross(direction).normalize();
        Matrix3f rotation = new Matrix3f((float)right.x, (float)right.y, (float)right.z,
                (float)vertical.x, (float)vertical.y, (float)vertical.z,
                (float)-direction.x, (float)-direction.y, (float)-direction.z);
        Vec3 center = bounds(model.get()).getCenter();
        poses.pushPose();
        poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        poses.mulPose(new Quaternionf().setFromNormalized(rotation));
        poses.scale(1, 1, (float)lengthScale);
        poses.translate(-center.x, -center.y, -center.z);
        CachedBuffers.partial(model, Blocks.AIR.defaultBlockState()).color(color & 0xFFFFFF).disableDiffuse()
                .light(LightTexture.FULL_BRIGHT).renderInto(poses, buffers.getBuffer(material));
        poses.popPose();
    }
}
