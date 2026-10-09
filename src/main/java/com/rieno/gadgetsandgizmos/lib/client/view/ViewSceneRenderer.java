package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRefreshSchedule;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewSource;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

// Capture bounded live world views and share their textures across display surfaces
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class ViewSceneRenderer{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_SOURCES = 8;
    private static final int MAX_SIZE = 320;
    private static final float CAPTURE_DISTANCE = 96;
    private static final float PROJECTION_DISTANCE = 1024;
    private static IntSupplier minimumRefreshRate = () -> 10;
    // Retain GPU resources only while a display continues requesting its source
    private static final Map<ViewReference, Capture> CAPTURES = new LinkedHashMap<>();
    private static @Nullable ClientLevel owningLevel;
    private static @Nullable Capture active;
    private static @Nullable ViewPose pose;
    private static long frame;
    private static long nextId;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the capture facade
    private ViewSceneRenderer(){}
    // Supply a live client setting for each active feed's minimum update cadence
    public static void setMinimumRefreshRate(IntSupplier supplier){
        minimumRefreshRate = java.util.Objects.requireNonNull(supplier);
    }
    // Expose the isolated lightmap to vanilla and optional terrain renderers
    public static @Nullable LightTexture captureLightmap(){ return isCapturing() ? active.lightmap : null; }
    // Keep camera overrides on the render thread while asynchronous meshes compile
    public static boolean isCapturing(){ return active != null && RenderSystem.isOnRenderThread(); }
    // Supply the target override only during a secondary scene render
    public static @Nullable RenderTarget captureTarget(){ return isCapturing() ? active.target : null; }
    // Keep Veil and other secondary scene integrations away from pending player geometry
    public static @Nullable RenderBuffers captureBuffers(){ return isCapturing() ? active.buffers : null; }
    // Supply the camera override only during a secondary scene render
    public static @Nullable Camera captureCamera(){
        return !isCapturing() || !VeilLevelPerspectiveRenderer.isRenderingPerspective() ? null : active.camera;
    }
    // Supply the lens override only during a secondary scene render
    public static @Nullable ViewPose capturePose(){ return isCapturing() ? pose : null; }
    // Limit secondary terrain and entity draws without changing the player's options
    public static float captureDistance(){ return active == null ? CAPTURE_DISTANCE : active.distance; }
    // Keep celestial geometry inside the projection while terrain remains bounded independently
    public static float projectionDistance(){ return PROJECTION_DISTANCE; }
    // Identify renderers whose terrain storage belongs to a secondary scene
    public static boolean ownsRenderer(LevelRenderer renderer){
        return CAPTURES.values().stream().anyMatch(capture -> capture.renderer == renderer);
    }
    // Retire cached meshes when the player's renderer reloads shaders or resources
    public static void invalidateScenes(LevelRenderer src){
        if(ownsRenderer(src)) return;
        CAPTURES.values().forEach(capture -> capture.reload = true);
    }
    // Keep sub-level compilation, uploads and layer batches inside the active scene
    public static @Nullable SubLevelRenderDispatcher captureSubLevelDispatcher(){
        if(!isCapturing()) return null;
        if(active.dispatcher == null) active.dispatcher = SubLevelRenderer.DEFAULT.create();
        return active.dispatcher;
    }
    // Keep independent terrain meshes current when the main client receives block updates
    public static void updateSection(LevelRenderer src, int x, int y, int z){
        if(ownsRenderer(src)) return;
        CAPTURES.values().forEach(capture -> {
            if(capture.renderer != null) capture.renderer.setSectionDirty(x, y, z);
        });
    }
    // Notify independent occlusion graphs when another client chunk becomes available
    public static void updateChunk(LevelRenderer src, ChunkPos pos){
        if(ownsRenderer(src)) return;
        CAPTURES.values().forEach(capture -> {
            if(capture.renderer != null) capture.renderer.onChunkLoaded(pos);
        });
    }
    // Resolve a ship's secondary meshes without replacing its main-view render data
    public static SubLevelRenderData subLevelData(ClientSubLevel subLevel, SubLevelRenderData original){
        if(!isCapturing()) return original;
        ShipCapture ship = active.ships.get(subLevel);
        if(ship == null || ship.original != original || ship.invalid){
            if(ship != null) ship.data.close();
            ship = new ShipCapture(original, SubLevelRenderDispatcher.get().createRenderData(subLevel));
            active.ships.put(subLevel, ship);
        }
        return ship.data;
    }
    // Defer replacement of ship meshes until their owning feed next renders
    public static void invalidateSubLevel(ClientSubLevel subLevel){
        CAPTURES.values().forEach(capture -> {
            ShipCapture ship = capture.ships.get(subLevel);
            if(ship != null) ship.invalid = true;
        });
    }
    // Forward dirty sections while retaining separate ship visibility and buffers
    public static void updateSubLevelSection(SubLevelRenderData src, int x, int y, int z, boolean immediate){
        CAPTURES.values().forEach(capture -> {
            ShipCapture ship = capture.ships.get(src.getSubLevel());
            if(ship != null && ship.original == src) ship.data.setDirty(x, y, z, immediate);
        });
    }
    // Queue a source and return its most recently completed texture
    public static @Nullable ResourceLocation request(ViewReference ref, int width, int height){
        Minecraft mc = Minecraft.getInstance();
        if(isCapturing() || mc.level == null || ref.resolve(mc.level) == null) return null;
        Capture capture = CAPTURES.get(ref);
        if(capture == null){
            if(CAPTURES.size() >= MAX_SOURCES) return null;
            // Displays can request new feeds inside the player's world or GUI draw
            Runnable restore = ViewSceneEnvironment.save();
            try{
                capture = new Capture();
            }finally{
                restore.run();
            }
            CAPTURES.put(ref, capture);
        }
        capture.requested = frame;
        double scale = Math.min(1.0D, MAX_SIZE / (double) Math.max(1, Math.max(width, height)));
        // Shared displays may grow a target but cannot repeatedly resize it down
        capture.width = Math.max(capture.width, Math.max(16, (int) (width * scale)));
        capture.height = Math.max(capture.height, Math.max(16, (int) (height * scale)));
        return capture.ready ? capture.texture : null;
    }
    // Draw the framebuffer with its native vertical texture orientation
    public static boolean drawFitted(ViewReference ref, int width, int height, float z,
                                     PoseStack stack, MultiBufferSource buffers){
        ResourceLocation texture = request(ref, width, height);
        Capture capture = CAPTURES.get(ref);
        if(texture == null || capture == null || capture.target == null) return false;
        double scale = Math.min(width / (double) capture.target.width, height / (double) capture.target.height);
        float w = (float) (capture.target.width * scale);
        float h = (float) (capture.target.height * scale);
        stack.pushPose();
        stack.translate((width - w) / 2, (height - h) / 2, 0);
        drawTexture(texture, w, h, z, stack, buffers);
        stack.popPose();
        return true;
    }
    // Draw the framebuffer with its native vertical texture orientation
    public static boolean draw(ViewReference ref, int width, int height, float z,
                               PoseStack stack, MultiBufferSource buffers){
        ResourceLocation texture = request(ref, width, height);
        if(texture == null) return false;
        drawTexture(texture, width, height, z, stack, buffers);
        return true;
    }
    // Share quad geometry while fitted surfaces retain the captured aspect ratio
    private static void drawTexture(ResourceLocation texture, float width, float height, float z,
                                    PoseStack stack, MultiBufferSource buffers){
        var vertices = buffers.getBuffer(RenderType.text(texture));
        Matrix4f matrix = stack.last().pose();
        vertices.addVertex(matrix, 0, 0, z).setColor(-1).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, 0, height, z).setColor(-1).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, width, height, z).setColor(-1).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, width, 0, z).setColor(-1).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
    }
    // Refresh every due feed independently before the player's frame
    @SubscribeEvent
    public static void render(RenderFrameEvent.Pre evt){
        Minecraft mc = Minecraft.getInstance();
        frame++;
        if(owningLevel != mc.level){
            clear();
            owningLevel = mc.level;
        }
        CAPTURES.entrySet().removeIf(entry -> {
            if(frame - entry.getValue().requested <= 120 && entry.getKey().resolve(mc.level) != null) return false;
            entry.getValue().close();
            return true;
        });
        if(mc.level == null || mc.player == null || mc.gameMode == null
                || VeilLevelPerspectiveRenderer.isRenderingPerspective()) return;
        int rate = Math.clamp(minimumRefreshRate.getAsInt(), 1, 60);
        for(var entry : CAPTURES.entrySet()){
            Capture capture = entry.getValue();
            if(frame - capture.requested > 2 || !capture.schedule.ready(System.nanoTime(), rate)) continue;
            ViewSource src = entry.getKey().resolve(mc.level);
            if(src != null) capture(mc, capture, src, evt);
        }
    }
    // Scope renderer, atmosphere and lightmap changes to one secondary scene
    private static void capture(Minecraft mc, Capture capture, ViewSource src, RenderFrameEvent.Pre evt){
        capture.distance = Math.min(CAPTURE_DISTANCE, Math.max(1, mc.options.getEffectiveRenderDistance()) * 16.0F);
        Runnable restoreEnvironment = ViewSceneEnvironment.save();
        LevelRenderer prevRenderer = mc.levelRenderer;
        Matrix4f prevProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var prevSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        mc.getProfiler().push("remote_view");
        long started = System.nanoTime();
        active = capture;
        Runnable restoreTargets = () -> {};
        try{
            if(capture.target.width != capture.width || capture.target.height != capture.height){
                capture.resize();
            }
            capture.lightmap.tick();
            capture.lightmap.updateLightTexture(evt.getPartialTick().getGameTimeDeltaPartialTick(false));
            capture.lightmap.turnOnLightLayer();
            src.updateViewEffects(evt.getPartialTick().getGameTimeDeltaPartialTick(false));
            pose = src.viewPose(evt.getPartialTick().getGameTimeDeltaPartialTick(false));
            capture.target.bindWrite(true);
            capture.camera.setup(mc.level, mc.player, false, false,
                    evt.getPartialTick().getGameTimeDeltaPartialTick(false));
            ((ViewCameraAccess) capture.camera).gadgetsngizmos$applyView(pose);
            if(capture.reload){
                capture.releaseScene();
                capture.reload = false;
            }
            if(capture.renderer == null){
                capture.renderer = new LevelRenderer(mc, mc.getEntityRenderDispatcher(),
                        mc.getBlockEntityRenderDispatcher(), capture.buffers);
                ((ViewSceneWorldAccess) mc).gadgetsngizmos$swapViewRenderer(capture.renderer);
                capture.renderer.setLevel(mc.level);
            }else{
                ((ViewSceneWorldAccess) mc).gadgetsngizmos$swapViewRenderer(capture.renderer);
            }
            ViewSceneAccess scene = (ViewSceneAccess) capture.renderer;
            scene.gadgetsngizmos$prepareView(pose.position());
            // Veil restores perspective draw lists after rendering; rebuild this scene's next list
            capture.renderer.needsUpdate();
            restoreTargets = scene.gadgetsngizmos$suspendViewTargets();
            var subLevels = SubLevelContainer.getContainer(mc.level).getAllSubLevels();
            capture.ships.entrySet().removeIf(entry -> {
                if(subLevels.contains(entry.getKey())) return false;
                entry.getValue().data.close();
                return true;
            });
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(pose.fov()),
                    capture.width / (float) capture.height, 0.05F,
                    PROJECTION_DISTANCE);
            Quaternionf rotation = new Quaternionf(pose.orientation());
            Quaternionf inverse = new Quaternionf(rotation).conjugate();
            // Veil appends the camera rotation to its base matrix; retain the inverse world view
            Matrix4f view = new Matrix4f().rotation(inverse).rotate(inverse);
            VeilLevelPerspectiveRenderer.render(capture.framebuffer, mc.player, view, projection,
                    new Vector3d(pose.position().x, pose.position().y, pose.position().z), rotation,
                    capture.distance / 16.0F, evt.getPartialTick(), false);
            capture.target.bindWrite(false);
            capture.ready = true;
            capture.rendered = frame;
        }finally{
            try{
                restoreTargets.run();
            }finally{
                active = null;
                pose = null;
                ((ViewSceneWorldAccess) mc).gadgetsngizmos$swapViewRenderer(prevRenderer);
                Camera camera = mc.gameRenderer.getMainCamera();
                mc.getBlockEntityRenderDispatcher().prepare(mc.level, camera, mc.hitResult);
                mc.getEntityRenderDispatcher().prepare(mc.level, camera, mc.crosshairPickEntity);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(prevProjection, prevSorting);
                try{
                    restoreEnvironment.run();
                }finally{
                    mc.getProfiler().pop();
                    capture.schedule.captured(started);
                }
            }
        }
    }
    // Release all secondary renderers and GPU resources owned by the current world
    public static void clear(){
        CAPTURES.values().forEach(Capture::close);
        CAPTURES.clear();
    }
    // Retain one source's world renderer, ship meshes and framebuffer
    private static final class Capture{
        private final RenderBuffers buffers = new RenderBuffers(1);
        private final TextureTarget target = new TextureTarget(16, 16, true, Minecraft.ON_OSX);
        private AdvancedFbo framebuffer = framebuffer();
        private final Camera camera = new Camera();
        private final LightTexture lightmap = new LightTexture(Minecraft.getInstance().gameRenderer, Minecraft.getInstance());
        private final ViewRefreshSchedule schedule = new ViewRefreshSchedule();
        private @Nullable LevelRenderer renderer;
        private @Nullable SubLevelRenderDispatcher dispatcher;
        private final Map<ClientSubLevel, ShipCapture> ships = new LinkedHashMap<>();
        private final ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                "gadgetsngizmos", "view/" + nextId++);
        private int width = 16;
        private int height = 16;
        private float distance = CAPTURE_DISTANCE;
        private long requested;
        private long rendered = -100;
        private boolean ready;
        private boolean reload;
        // Register a texture wrapper that leaves framebuffer ownership with this capture
        private Capture(){
            Minecraft.getInstance().getTextureManager().register(texture, new AbstractTexture(){
                // Return the current framebuffer colour attachment after resize
                @Override
                public int getId(){ return target.getColorTextureId(); }
                // Preserve the live GPU attachment during resource reload
                @Override
                public void load(ResourceManager manager){}
                // Leave attachment disposal to the owning framebuffer
                @Override
                public void releaseId(){}
            });
        }
        // Share the existing attachments with Veil without copying or owning their textures
        private AdvancedFbo framebuffer(){
            return AdvancedFbo.withSize(target.width, target.height)
                    .addColorTextureWrapper(target.getColorTextureId())
                    .setDepthTextureWrapper(target.getDepthTextureId()).build(true);
        }
        // Refresh attachment references only when the shared target grows
        private void resize(){
            framebuffer.free();
            target.resize(width, height, Minecraft.ON_OSX);
            framebuffer = framebuffer();
        }
        // Cancel old scene jobs before replacing their renderer or vertex layouts
        private void releaseScene(){
            Minecraft mc = Minecraft.getInstance();
            if(renderer != null) ((ViewSceneWorldAccess) mc).gadgetsngizmos$swapViewRenderer(renderer);
            ships.values().forEach(ship -> ship.data.close());
            ships.clear();
            if(dispatcher != null){
                dispatcher.free();
                dispatcher = null;
            }
            if(renderer != null){
                ((ViewSceneAccess) renderer).gadgetsngizmos$closeView();
                renderer = null;
            }
        }
        // Release both registrations and GPU attachments exactly once
        private void close(){
            Minecraft mc = Minecraft.getInstance();
            Runnable restore = ViewSceneEnvironment.save();
            LevelRenderer prevRenderer = mc.levelRenderer;
            Capture prev = active;
            active = this;
            try{
                releaseScene();
                ViewSceneBuffers.close(buffers);
                mc.getTextureManager().release(texture);
                framebuffer.free();
                target.destroyBuffers();
                mc.getTextureManager().release(((com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneLightmapAccess)
                        lightmap).gadgetsngizmos$lightmapLocation());
            }finally{
                ((ViewSceneWorldAccess) mc).gadgetsngizmos$swapViewRenderer(prevRenderer);
                active = prev;
                restore.run();
            }
        }
    }
    // Keep a feed's ship mesh cache tied to the corresponding main-view allocation
    private static final class ShipCapture{
        private final SubLevelRenderData original;
        private final SubLevelRenderData data;
        private boolean invalid;
        // Retain the main allocation identity so resizing can retire secondary meshes
        private ShipCapture(SubLevelRenderData original, SubLevelRenderData data){
            this.original = original;
            this.data = data;
        }
    }
}
