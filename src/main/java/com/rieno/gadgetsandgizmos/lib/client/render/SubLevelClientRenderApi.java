package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.function.Supplier;
import java.util.function.Consumer;

// Run client rendering with live SubLevel poses
public final class SubLevelClientRenderApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SubLevel client render API
    private SubLevelClientRenderApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run one action with interpolated SubLevel poses
    public static <T> T withPoses(ClientLevel level, float partialTicks, Supplier<T> action) {
        LevelPoseProviderExtension poses = (LevelPoseProviderExtension) level;
        Function<SubLevel, Pose3dc> poseProvider = key -> key instanceof ClientSubLevel clientSubLevel
                ? clientSubLevel.renderPose(partialTicks)
                : ((SubLevel) key).logicalPose();
        poses.sable$pushPoseSupplier(poseProvider);
        try {
            return action.get();
        } finally {
            poses.sable$popPoseSupplier();
        }
    }

    // Get one SubLevel render position
    public static Vec3 renderPosition(ClientSubLevel subLevel, float partialTicks) {
        Vector3dc pos = subLevel.renderPose(partialTicks).position();
        return new Vec3(pos.x(), pos.y(), pos.z());
    }

    // Draw model-view transformed geometry with the world's projection and restore render state
    public static void withProjection(Matrix4fc projection, Runnable action){
        Matrix4f next = new Matrix4f(projection);
        Matrix4f prev = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        try{
            view.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(next, VertexSorting.DISTANCE_TO_ORIGIN);
            action.run();
        }finally{
            RenderSystem.setProjectionMatrix(prev, sorting);
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // Flush projection geometry independently of the world's pending render buffers
    public static void withProjection(Matrix4fc projection, Consumer<MultiBufferSource.BufferSource> action){
        withProjection(projection, () -> {
            var buffers = ProjectionBuffers.BUFFERS;
            try{ action.accept(buffers); }
            finally{ buffers.endBatch(); }
        });
    }

    // Anchor local overlay geometry with the interpolated Sable pose before converting to floats
    public static Matrix4f localModelView(SubLevel subLevel, float partialTicks, Vec3 origin,
                                          Vec3 camera, Matrix4fc view){
        Pose3dc pose = subLevel instanceof ClientSubLevel client ? client.renderPose(partialTicks)
                : subLevel == null ? null : subLevel.logicalPose();
        return localModelView(pose, origin, camera, view);
    }

    // Keep plot coordinates out of float matrices and apply the body's rotation and scale once
    public static Matrix4f localModelView(Pose3dc pose, Vec3 origin, Vec3 camera, Matrix4fc view){
        Vec3 world = pose == null ? origin : pose.transformPosition(origin);
        Matrix4f res = new Matrix4f(view).translate((float) (world.x - camera.x),
                (float) (world.y - camera.y), (float) (world.z - camera.z));
        if(pose != null){
            Vector3dc scale = pose.scale();
            res.rotate(new Quaternionf(pose.orientation())).scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        }
        return res;
    }

    // Give detached renderers a local origin anchored to a real block in the interpolated body frame
    public static Pose3dc anchoredPose(ClientSubLevel subLevel, float partialTicks, Vec3 origin,
            Vec3 anchor, Quaterniondc localRotation){
        Pose3dc pose = subLevel.renderPose(partialTicks);
        Vec3 world = pose.transformPosition(anchor);
        return new Pose3d(new Vector3d(world.x, world.y, world.z),
                new Quaterniond(pose.orientation()).mul(localRotation),
                new Vector3d(origin.x, origin.y, origin.z), new Vector3d(pose.scale()));
    }

    private static final class ProjectionBuffers{
        private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(8192));
    }
}
