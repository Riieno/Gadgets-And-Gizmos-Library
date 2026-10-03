package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.util.Collection;

// Render block-only snapshots as translucent, full-bright world holograms
public final class WorldBlockHologramRenderer{
    private WorldBlockHologramRenderer(){}

    // Draw a preview without loading its source sublevel or block entities
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                              Vec3 target, Quaterniond rotation,
                              Collection<SubLevelPreviewRenderer.SnapshotBlock> blocks){
        if(blocks == null || blocks.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource ghost = type -> new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()));
        pose.pushPose();
        pose.translate(target.x - camera.x, target.y - camera.y, target.z - camera.z);
        pose.mulPose(new Quaternionf(rotation));
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for(var block : blocks){
            Vec3 point = block.rootPosition();
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x + 1); maxY = Math.max(maxY, point.y + 1); maxZ = Math.max(maxZ, point.z + 1);
            pose.pushPose();
            pose.translate(point.x, point.y, point.z);
            pose.translate(0.5D, 0.5D, 0.5D);
            pose.mulPose(block.orientation());
            pose.translate(-0.5D, -0.5D, -0.5D);
            minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, ghost,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
        }
        buffers.endBatch(RenderType.translucent());
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                0.25F, 0.9F, 1.0F, 0.92F);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static final class GhostVertexConsumer implements VertexConsumer{
        private final VertexConsumer target;

        private GhostVertexConsumer(VertexConsumer target){ this.target = target; }
        @Override public VertexConsumer addVertex(float x, float y, float z){ target.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){
            target.setColor((red + 50) / 2, (green + 170) / 2, (blue + 220) / 2,
                    Math.max(20, alpha * 2 / 5));
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v){ target.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ target.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v){ target.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ target.setNormal(x, y, z); return this; }
    }
}
