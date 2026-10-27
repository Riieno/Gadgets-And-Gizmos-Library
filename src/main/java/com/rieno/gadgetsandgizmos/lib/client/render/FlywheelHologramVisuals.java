package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.task.TaskExecutor;
import dev.engine_room.flywheel.api.visual.*;
import dev.engine_room.flywheel.api.visualization.*;
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Render native Flywheel visualizers in miniature embeddings owned by the world's backend
public final class FlywheelHologramVisuals implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final TaskExecutor DIRECT = new TaskExecutor(){
        @Override public int threadCount(){ return 1; }
        @Override public void execute(Runnable task){ task.run(); }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Scene effect;
    private final Map<BlockEntity, BlockState> requested = new IdentityHashMap<>();
    private boolean registered;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public FlywheelHologramVisuals(ClientLevel level, Function<Float, HologramProjectionFrame> frame){
        effect = new Scene(level, frame);
    }

    public void beginFrame(){ requested.clear(); }

    // Native visualizers retain their own partial models and animation plans
    public boolean request(BlockEntity entity, BlockState state){
        if(!VisualizationManager.supportsVisualization(effect.level) || effect.failed.contains(entity)
                || !VisualizationHelper.canVisualize(entity)) return false;
        if(!registered){ VisualizationHelper.queueAdd(effect); registered = true; }
        requested.put(entity, state);
        return true;
    }

    public void endFrame(){
        effect.desired = Map.copyOf(requested);
        effect.lastVisible = System.nanoTime();
    }

    @Override
    public void close(){
        effect.closed = true;
        effect.desired = Map.of();
        if(registered) VisualizationHelper.queueRemove(effect);
        requested.clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Keep meshes and texture choices while replacing only the hologram's lighting and blend state
    static Model hologramModel(Model model){
        var meshes = model.meshes().stream().map(mesh -> new Model.ConfiguredMesh(
                SimpleMaterial.builderOf(mesh.material()).useLight(false).useOverlay(false)
                        .cardinalLightingMode(CardinalLightingMode.OFF).ambientOcclusion(false).fog(FogShaders.NONE)
                        .transparency(Transparency.TRANSLUCENT).writeMask(WriteMask.COLOR_DEPTH).build(), mesh.mesh())).toList();
        return new Model(){
            @Override public java.util.List<ConfiguredMesh> meshes(){ return meshes; }
            @Override public Vector4fc boundingSphere(){ return model.boundingSphere(); }
        };
    }

    private static final class Scene implements Effect{
        private final ClientLevel level;
        private final Function<Float, HologramProjectionFrame> frame;
        private final Set<BlockEntity> failed = ConcurrentHashMap.newKeySet();
        private volatile Map<BlockEntity, BlockState> desired = Map.of();
        private volatile long lastVisible;
        private volatile boolean closed;
        private Scene(ClientLevel level, Function<Float, HologramProjectionFrame> frame){ this.level = level; this.frame = frame; }
        @Override public LevelAccessor level(){ return level; }
        @Override public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick){ return new SceneVisual(this, ctx); }
    }

    private static final class SceneVisual implements EffectVisual<Scene>, DynamicVisual, TickableVisual{
        private final Scene scene;
        private final VisualizationContext ctx;
        private final Map<BlockEntity, Child> children = new IdentityHashMap<>();
        private final Map<Model, Model> materials = new IdentityHashMap<>();
        private SceneVisual(Scene scene, VisualizationContext ctx){ this.scene = scene; this.ctx = ctx; }

        @Override public Plan<DynamicVisual.Context> planFrame(){ return SimplePlan.of(this::frame); }
        @Override public Plan<TickableVisual.Context> planTick(){
            return SimplePlan.of(tick -> {
                try(var scope = HologramRenderScope.enter()){
                    children.values().forEach(child -> {
                        if(child.visual instanceof TickableVisual tickable) tickable.planTick().execute(DIRECT, tick);
                    });
                }
            });
        }
        private void frame(DynamicVisual.Context frame){
            try(var scope = HologramRenderScope.enter()){
                renderFrame(frame);
            }
        }
        private void renderFrame(DynamicVisual.Context frame){
            Map<BlockEntity, BlockState> desired = scene.closed || System.nanoTime() - scene.lastVisible > 250_000_000L ? Map.of() : scene.desired;
            children.entrySet().removeIf(entry -> {
                if(desired.get(entry.getKey()) == entry.getValue().state && !entry.getKey().isRemoved()) return false;
                entry.getValue().close();
                return true;
            });
            if(desired.isEmpty()) return;
            HologramProjectionFrame projection = scene.frame.apply(frame.partialTick());
            if(projection == null) return;
            desired.forEach((entity, state) -> {
                if(entity.isRemoved() || scene.failed.contains(entity)) return;
                Child child = children.get(entity);
                if(child == null){
                    VisualEmbedding embedding = ctx.createEmbedding(entity.getBlockPos());
                    Set<Instance> owned = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                    try{
                        var visualizer = VisualizationHelper.getVisualizer(entity);
                        if(visualizer == null){ embedding.delete(); return; }
                        var visual = visualizer.createVisual(new HologramEmbedding(embedding, materials, owned), entity, frame.partialTick());
                        if(visual == null){ owned.forEach(Instance::delete); embedding.delete(); scene.failed.add(entity); return; }
                        child = new Child(state, visual, embedding, owned);
                        children.put(entity, child);
                    }catch(RuntimeException err){
                        owned.forEach(Instance::delete); embedding.delete(); scene.failed.add(entity);
                        org.slf4j.LoggerFactory.getLogger(FlywheelHologramVisuals.class).warn("Unable to create hologram visual for {}", state, err);
                        return;
                    }
                }
                var source = SubLevelClientRenderApi.worldFrame(entity, frame.partialTick());
                Matrix4f pose = projection.blockTransform(ctx.renderOrigin(), source);
                child.embedding.transforms(pose, pose.normal(new Matrix3f()));
                child.visual.update(frame.partialTick());
                if(child.visual instanceof DynamicVisual dynamic) dynamic.planFrame().execute(DIRECT, frame);
                child.owned.forEach(instance -> {
                    if(instance instanceof ColoredLitInstance colored){ colored.color(90, 210, 255, 190).light(LightTexture.FULL_BRIGHT); colored.setChanged(); }
                });
            });
        }
        @Override public void update(float partialTick){}
        @Override public void delete(){ children.values().forEach(Child::close); children.clear(); materials.clear(); }
    }

    private record Child(BlockState state, BlockEntityVisual<?> visual, VisualEmbedding embedding, Set<Instance> owned){
        private void close(){ visual.delete(); embedding.delete(); owned.clear(); }
    }

    private static final class HologramEmbedding implements VisualEmbedding{
        private final VisualEmbedding delegate;
        private final Map<Model, Model> materials;
        private final Set<Instance> owned;
        private HologramEmbedding(VisualEmbedding delegate, Map<Model, Model> materials, Set<Instance> owned){
            this.delegate = delegate; this.materials = materials; this.owned = owned;
        }
        @Override public InstancerProvider instancerProvider(){
            return new InstancerProvider(){
                @Override public <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias){
                    Instancer<I> target = delegate.instancerProvider().instancer(type, materials.computeIfAbsent(model, FlywheelHologramVisuals::hologramModel), bias);
                    return new Instancer<>(){
                        @Override public I createInstance(){ I instance = target.createInstance(); owned.add(instance); return instance; }
                        @Override public void stealInstance(I instance){ target.stealInstance(instance); if(instance != null) owned.add(instance); }
                    };
                }
            };
        }
        @Override public net.minecraft.core.Vec3i renderOrigin(){ return delegate.renderOrigin(); }
        @Override public VisualEmbedding createEmbedding(net.minecraft.core.Vec3i origin){ return new HologramEmbedding(delegate.createEmbedding(origin), materials, owned); }
        @Override public void transforms(Matrix4fc pose, Matrix3fc normal){ delegate.transforms(pose, normal); }
        @Override public void delete(){ delegate.delete(); }
    }
}
