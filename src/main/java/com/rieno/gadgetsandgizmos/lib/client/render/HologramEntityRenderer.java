package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Render isolated miniature entity models without adding them to the client level
public final class HologramEntityRenderer{
    private final Map<UUID, Entity> models = new HashMap<>();
    private final Map<UUID, ProjectionSmoothing> motion = new HashMap<>();
    private final Map<PlayerSkin.Model, PlayerRenderer> players = new EnumMap<>(PlayerSkin.Model.class);

    // The supplied pose uses the same scale and source origin as the terrain renderer
    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, List<HologramEntity> snapshots, float partialTicks){
        render(poses, buffers, origin, snapshots, partialTicks, entity -> true);
    }

    // Let a consumer hide categories immediately while fresh server snapshots are in flight
    public void render(PoseStack poses, MultiBufferSource buffers, Vec3 origin, List<HologramEntity> snapshots,
                       float partialTicks, java.util.function.Predicate<Entity> visible){
        try(var scope = HologramRenderScope.enter()){
            renderEntities(poses, buffers, origin, snapshots, partialTicks, visible);
        }
    }

    private void renderEntities(PoseStack poses, MultiBufferSource buffers, Vec3 origin, List<HologramEntity> snapshots,
                                float partialTicks, java.util.function.Predicate<Entity> visible){
        var mc = Minecraft.getInstance();
        if(mc.level == null) return;
        var retained = new HashSet<UUID>();
        for(var snapshot : snapshots){
            retained.add(snapshot.id());
            Entity entity = models.computeIfAbsent(snapshot.id(), id -> snapshot.type().getPath().equals("player")
                    ? new RemotePlayer(mc.level, new GameProfile(id, snapshot.name()))
                    : BuiltInRegistries.ENTITY_TYPE.get(snapshot.type()).create(mc.level));
            if(entity == null || !visible.test(entity)) continue;
            entity.setUUID(snapshot.id());
            Vec3 point = motion.computeIfAbsent(snapshot.id(), ignored -> new ProjectionSmoothing())
                    .update(snapshot.pos(), (mc.level.getGameTime() + partialTicks) / 20D);
            entity.setPos(point);
            entity.setYRot(snapshot.yaw());
            entity.yRotO = snapshot.yaw();
            entity.tickCount = (int)mc.level.getGameTime();
            if(entity instanceof LivingEntity living){ living.yBodyRot = living.yBodyRotO = snapshot.yaw(); }
            EntityRenderer<? super Entity> renderer = renderer(mc, entity);
            var texture = renderer.getTextureLocation(entity);
            MultiBufferSource ghost = hologramBuffers(buffers, texture);
            poses.pushPose();
            poses.translate(point.x - origin.x, point.y - origin.y, point.z - origin.z);
            try{ renderer.render(entity, snapshot.yaw(), partialTicks, poses, ghost, LightTexture.FULL_BRIGHT); }
            finally{ poses.popPose(); }
        }
        models.keySet().retainAll(retained);
        motion.keySet().retainAll(retained);
    }

    public void clear(){ models.clear(); motion.clear(); players.clear(); }

    // Keep miniature poses and modded skin layers away from the renderer used for first-person arms
    @SuppressWarnings("unchecked")
    private <T extends Entity> EntityRenderer<? super T> renderer(Minecraft mc, T entity){
        if(!(entity instanceof AbstractClientPlayer player)) return mc.getEntityRenderDispatcher().getRenderer(entity);
        return (EntityRenderer<? super T>)players.computeIfAbsent(player.getSkin().model(), model -> new PlayerRenderer(
                new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderer(),
                        mc.gameRenderer.itemInHandRenderer, mc.getResourceManager(), mc.getEntityModels(), mc.font),
                model == PlayerSkin.Model.SLIM));
    }

    // Keep text, lines and modded vertex layouts on their original buffers
    static MultiBufferSource hologramBuffers(MultiBufferSource buffers, ResourceLocation texture){
        return type -> new Tint(buffers.getBuffer(type.format() == DefaultVertexFormat.NEW_ENTITY
                ? RenderType.entityTranslucentCull(texture) : type));
    }

    private record Tint(VertexConsumer target) implements VertexConsumer{
        @Override public VertexConsumer addVertex(float x, float y, float z){ target.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ target.setColor((r + 30) / 2, (g + 170) / 2, (b + 240) / 2, a * 3 / 4); return this; }
        @Override public VertexConsumer setUv(float u, float v){ target.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v){ target.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v){ target.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ target.setNormal(x, y, z); return this; }
    }
}
