package com.rieno.gadgetsandgizmos.lib.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SurfaceFloodlight;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.event.level.LevelEvent;

// Light the hit material directly so bright near beams stay confined to their footprint
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class SurfaceFloodlightRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<SurfaceFloodlight.Patch, Mesh> MESHES = new HashMap<>();
    private static final Map<Integer, SceneDepth> DEPTHS = new HashMap<>();
    private static net.minecraft.world.level.Level owningLevel;
    private static long trimmedTick;
    private static ShaderInstance shader;
    private static Scene mainScene;
    private static long frame;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private SurfaceFloodlightRenderer(){}

    // Require fresh opaque visibility for every player frame and secondary capture
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginFrame(RenderFrameEvent.Pre evt){ frame++; }
    // Retain opaque visibility before particles and translucent effects replace scene depth
    @SubscribeEvent
    public static void render(RenderLevelStageEvent evt){
        var mc = Minecraft.getInstance();
        if(evt.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES){
            if(mc.level != null && !SurfaceFloodlight.patches(mc.level).isEmpty()) snapshotDepth(true);
            return;
        }
        if(evt.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ViewSceneRenderer.isCapturing()) return;
        mainScene = mc.level == null || SurfaceFloodlight.patches(mc.level).isEmpty() ? null
                : new Scene(mc.level, evt.getCamera().getPosition(), new Matrix4f(evt.getModelViewMatrix()),
                new Matrix4f(evt.getProjectionMatrix()), evt.getPartialTick().getGameTimeDeltaPartialTick(false));
        renderMainScene();
    }
    // Draw into the completed world before the hand clears its depth buffer
    public static void renderMainScene(){
        Scene scene = mainScene;
        mainScene = null;
        if(scene != null) renderScene(scene.level, scene.camera, scene.view, scene.projection, scene.partialTick);
    }
    // Use one material shader independently of terrain, shadow and deferred light passes
    public static void registerShader(RegisterShadersEvent evt) throws IOException{
        evt.registerShader(new ShaderInstance(evt.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "surface_floodlight"),
                DefaultVertexFormat.POSITION_TEX_COLOR), val -> shader = val);
    }
    // Draw lighting in a secondary scene's current framebuffer after its terrain completes
    public static void renderScene(Level level, Vec3 camera, Matrix4f view, Matrix4f projection){
        renderScene(level, camera, view, projection, 1);
    }
    // Match moving hit surfaces to the same interpolated pose used by their world geometry
    public static void renderScene(Level level, Vec3 camera, Matrix4f view, Matrix4f projection, float partialTick){
        if(shader == null || level == null || SurfaceFloodlight.patches(level).isEmpty()) return;
        SceneDepth sceneDepth = DEPTHS.get(depthAttachment());
        if(sceneDepth == null || sceneDepth.frame != frame || !sceneDepth.opaque) sceneDepth = snapshotDepth(false);
        var frustum = new Frustum(view, projection);
        frustum.prepare(camera.x, camera.y, camera.z);
        var modelView = RenderSystem.getModelViewStack();
        var prevProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var prevSorting = RenderSystem.getVertexSorting();
        float[] color = RenderSystem.getShaderColor().clone();
        ShaderInstance prevShader = RenderSystem.getShader();
        int prevTexture = RenderSystem.getShaderTexture(0);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int prevDepthBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float factor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        float units = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        modelView.pushMatrix();
        try{
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, prevSorting);
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            shader.safeGetUniform("HasSceneDepth").set(sceneDepth == null ? 0 : 1);
            shader.safeGetUniform("InverseProjectionMat").set(new Matrix4f(projection).invert());
            if(sceneDepth != null){
                shader.setSampler("SceneDepth", sceneDepth.texture);
                // Sample normalized visibility even when a shader scales its terrain framebuffer
                try(var storage = MemoryStack.stackPush()){
                    var viewport = storage.mallocInt(4);
                    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                    shader.safeGetUniform("DepthViewport").set((float) viewport.get(0), (float) viewport.get(1),
                            (float) viewport.get(2), (float) viewport.get(3));
                }
            }
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            if(sceneDepth == null) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disablePolygonOffset();
            var stack = new PoseStack();
            stack.mulPose(view);
            draw(level, stack, camera, frustum, partialTick);
        }finally{
            RenderSystem.setShader(() -> prevShader);
            RenderSystem.setShaderTexture(0, prevTexture);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(prevBinding);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(prevDepthBinding);
            GlStateManager._activeTexture(prevActive);
            GlStateManager._glUseProgram(prevProgram);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthWrite);
            RenderSystem.polygonOffset(factor, units);
            if(polygonOffset) RenderSystem.enablePolygonOffset(); else RenderSystem.disablePolygonOffset();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(prevProjection, prevSorting);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        }
    }
    // Share cached hit materials while each caller supplies its own camera and render target
    private static void draw(Level root, PoseStack stack, Vec3 camera, Frustum frustum, float partialTick){
        if(root == null) return;
        var mc = Minecraft.getInstance();
        if(owningLevel != root){ MESHES.clear(); owningLevel = root; trimmedTick = Long.MIN_VALUE; }
        var patches = SurfaceFloodlight.patches(root);
        if(patches.isEmpty()){ MESHES.clear(); return; }
        if(trimmedTick == Long.MIN_VALUE || root.getGameTime() - trimmedTick >= 20){
            MESHES.keySet().retainAll(new HashSet<>(patches));
            trimmedTick = root.getGameTime();
        }
        stack.pushPose();
        try(var storage = new ByteBufferBuilder(4096)){
            var vertices = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int count = 0;
            for(var patch : patches){
                var body = SableLevelApi.subLevel(root, patch.subLevelId());
                if(patch.subLevelId() != null && body == null) continue;
                var bodyPose = body instanceof ClientSubLevel client ? client.renderPose(partialTick) : body == null ? null : body.logicalPose();
                var level = body == null ? root : body.getLevel();
                if(!level.hasChunkAt(patch.pos())) continue;
                var state = level.getBlockState(patch.pos());
                if(state.isAir()) continue;
                Vec3 worldCenter = bodyPose == null ? patch.center() : bodyPose.transformPosition(patch.center());
                if(worldCenter.distanceToSqr(camera) > 256 * 256) continue;
                Vec3 blockCenter = bodyPose == null ? patch.pos().getCenter() : bodyPose.transformPosition(patch.pos().getCenter());
                if(!frustum.isVisible(new net.minecraft.world.phys.AABB(blockCenter.add(-1,-1,-1),blockCenter.add(1,1,1)))) continue;
                var model = mc.getBlockRenderer().getBlockModel(state);
                Mesh mesh = MESHES.get(patch);
                if(mesh == null || mesh.state != state || mesh.model != model){
                    var random = RandomSource.create(state.getSeed(patch.pos()));
                    var quads = new ArrayList<>(model.getQuads(state, patch.face(), random));
                    for(BakedQuad quad : model.getQuads(state, null, random)){
                        if(quad.getDirection() == patch.face()) quads.add(quad);
                    }
                    List<LitVertex> geometry = new ArrayList<>();
                    for(BakedQuad quad : quads){
                        int tint = quad.isTinted() ? mc.getBlockColors().getColor(state, level, patch.pos(), quad.getTintIndex()) : 0xFFFFFF;
                        build(geometry, patch, quad, tint);
                    }
                    mesh = new Mesh(state,model,List.copyOf(geometry));
                    if(MESHES.size() >= 2048) MESHES.clear();
                    MESHES.put(patch,mesh);
                }
                Vec3 normal = Vec3.atLowerCornerOf(patch.face().getNormal());
                if(bodyPose != null) normal = bodyPose.transformNormal(normal).normalize();
                for(LitVertex vertex : mesh.vertices){
                    Sample val = vertex.sample;
                    Vec3 world = bodyPose == null ? val.pos : bodyPose.transformPosition(val.pos);
                    vertices.addVertex(stack.last().pose(), (float) (world.x - camera.x + normal.x * 0.002),
                                    (float) (world.y - camera.y + normal.y * 0.002), (float) (world.z - camera.z + normal.z * 0.002))
                            .setColor(vertex.tint >> 16 & 255, vertex.tint >> 8 & 255, vertex.tint & 255, val.alpha)
                            .setUv(val.u, val.v);
                    count++;
                }
            }
            if(count > 0) BufferUploader.drawWithShader(vertices.buildOrThrow());
        }finally{
            stack.popPose();
        }
    }
    // Clear retained materials when the client world unloads
    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload evt){
        if(evt.getLevel() != owningLevel) return;
        MESHES.clear();
        owningLevel = null;
        mainScene = null;
        DEPTHS.values().forEach(SceneDepth::close);
        DEPTHS.clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Key visibility by the shared depth attachment even when shader framebuffers change
    private static int depthAttachment(){
        int target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(target == 0) return 0;
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        return type == GL11.GL_TEXTURE ? GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) : 0;
    }
    // Copy only depth from the current scene without reading back pixels or changing its target
    private static SceneDepth snapshotDepth(boolean opaque){
        int attachment = depthAttachment();
        if(attachment == 0) return null;
        int x, y, width, height;
        try(var stack = MemoryStack.stackPush()){
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            x = viewport.get(0); y = viewport.get(1); width = viewport.get(2); height = viewport.get(3);
        }
        if(width <= 0 || height <= 0) return null;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            if(DEPTHS.size() >= 16 && !DEPTHS.containsKey(attachment)){
                DEPTHS.values().forEach(SceneDepth::close);
                DEPTHS.clear();
            }
            SceneDepth depth = DEPTHS.computeIfAbsent(attachment, key -> new SceneDepth());
            GlStateManager._bindTexture(depth.texture);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            if(depth.width != width || depth.height != height){
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, x, y, width, height, 0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }else{
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
            }
            depth.width = width; depth.height = height;
            depth.frame = frame; depth.opaque = opaque;
            return depth;
        }finally{
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GlStateManager._bindTexture(prevBinding);
            GlStateManager._activeTexture(prevActive);
        }
    }
    // Subdivide only close patches so vertex fading also works within one block face
    private static void build(List<LitVertex> vertices, SurfaceFloodlight.Patch patch, BakedQuad quad, int tint){
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        if(stride < 6) return;
        int steps = patch.profile().radius() < 1 ? 8 : 2;
        for(int x = 0; x < steps; x++) for(int y = 0; y < steps; y++){
            Sample[] samples = new Sample[]{
                    sample(data, stride, (double) x / steps, (double) y / steps, patch),
                    sample(data, stride, (double) x / steps, (double) (y + 1) / steps, patch),
                    sample(data, stride, (double) (x + 1) / steps, (double) (y + 1) / steps, patch),
                    sample(data, stride, (double) (x + 1) / steps, (double) y / steps, patch)};
            if(java.util.Arrays.stream(samples).allMatch(val -> val.alpha == 0)) continue;
            for(Sample val : samples){
                vertices.add(new LitVertex(val,tint));
            }
        }
    }
    // Preserve the authored material UVs as the projected light crosses its surface
    private static Sample sample(int[] data, int stride, double x, double y, SurfaceFloodlight.Patch patch){
        double[] weights = {(1 - x) * (1 - y), (1 - x) * y, x * y, x * (1 - y)};
        double px = 0, py = 0, pz = 0, u = 0, v = 0;
        for(int idx = 0; idx < 4; idx++){
            int offset = idx * stride;
            px += Float.intBitsToFloat(data[offset]) * weights[idx];
            py += Float.intBitsToFloat(data[offset + 1]) * weights[idx];
            pz += Float.intBitsToFloat(data[offset + 2]) * weights[idx];
            u += Float.intBitsToFloat(data[offset + 4]) * weights[idx];
            v += Float.intBitsToFloat(data[offset + 5]) * weights[idx];
        }
        Vec3 pos = new Vec3(px + patch.pos().getX(), py + patch.pos().getY(), pz + patch.pos().getZ());
        int alpha = (int) Math.round(255 * patch.profile().intensity(pos.distanceTo(patch.center())));
        return new Sample(pos, (float) u, (float) v, alpha);
    }
    private record Sample(Vec3 pos, float u, float v, int alpha){}
    private record LitVertex(Sample sample,int tint){}
    private record Mesh(net.minecraft.world.level.block.state.BlockState state,
                        net.minecraft.client.resources.model.BakedModel model,List<LitVertex> vertices){}
    private record Scene(Level level, Vec3 camera, Matrix4f view, Matrix4f projection, float partialTick){}
    private static final class SceneDepth{
        private final int texture = GlStateManager._genTexture();
        private int width, height;
        private long frame;
        private boolean opaque;
        // Release each scene's private depth copy with its world
        private void close(){ GlStateManager._deleteTexture(texture); }
    }
}
