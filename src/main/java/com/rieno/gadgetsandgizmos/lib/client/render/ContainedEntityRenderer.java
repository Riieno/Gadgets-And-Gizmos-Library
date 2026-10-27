package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Consumer;

// Draw an animated display mob inside the caller's local pose without spawning it
public final class ContainedEntityRenderer<T extends Mob>{
    private final EntityType<T> type;
    private final Consumer<? super T> setup;
    private WeakReference<T> cached = new WeakReference<>(null);

    // Select the mob model while leaving its position and scale to the caller
    public ContainedEntityRenderer(EntityType<T> type){
        this(type, entity -> {});
    }

    // Configure equipment or other display state whenever a new mob is created
    public ContainedEntityRenderer(EntityType<T> type, Consumer<? super T> setup){
        this.type = Objects.requireNonNull(type, "Entity type is missing");
        this.setup = Objects.requireNonNull(setup, "Entity setup is missing");
    }

    // Render on the client thread without AI, world insertion or dispatcher shadows
    public void render(Level level, int age, float partialTicks, float yaw,
            PoseStack poses, MultiBufferSource buffers, int light){
        if(level == null || !level.isClientSide) return;
        T entity = cached.get();
        if(entity == null || entity.level() != level){
            entity = type.create(level);
            if(entity == null) return;
            entity.setNoAi(true);
            entity.setNoGravity(true);
            entity.setSilent(true);
            setup.accept(entity);
            cached = new WeakReference<>(entity);
        }
        entity.tickCount = age;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.yBodyRot = entity.yBodyRotO = yaw;
        entity.yHeadRot = entity.yHeadRotO = yaw;
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .render(entity, yaw, partialTicks, poses, buffers, light);
    }
}
