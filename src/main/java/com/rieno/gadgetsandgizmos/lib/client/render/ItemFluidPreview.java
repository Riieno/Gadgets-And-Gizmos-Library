package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import com.rieno.gadgetsandgizmos.lib.geometry.ClipBounds;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Replace an item's liquid cuboid with volume-weighted layers, optional waves and temporary mixing
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class ItemFluidPreview<K>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final WeightedColorGradient EMPTY = new WeightedColorGradient(List.of());
    private static int meshUpdates = 8;
    private final ResourceLocation texture;
    private final AABB bounds;
    private final float fill;
    private final Map<K, Map<ItemDisplayContext, Entry>> entries = new WeakHashMap<>();
    private final Map<ItemDisplayContext, Entry> heldEntries = new EnumMap<>(ItemDisplayContext.class);
    private java.lang.ref.WeakReference<Object> heldOwner = new java.lang.ref.WeakReference<>(null);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Identify one axis-aligned liquid cuboid by its atlas sprite and baked model bounds
    public ItemFluidPreview(ResourceLocation texture, AABB bounds, float fill){
        if(!Float.isFinite(fill) || fill < 0 || fill > 1) throw new IllegalArgumentException("Fluid fill must be between zero and one");
        this.texture = texture;
        this.bounds = bounds;
        this.fill = fill;
    }

    // Allow at most eight cached mesh refreshes per frame after initial presentations
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginFrame(RenderFrameEvent.Pre evt){ meshUpdates = 8; }

    // Drive one continuous dyed volume from world motion, retaining state across replacement held stacks
    public void render(K owner, ItemDisplayContext ctx, BakedModel model, DensityColorPalette palette,
                       boolean emissive, double time, PoseStack pose, MultiBufferSource buffers,
                       PartialItemModelRenderer renderer, int light, int overlay){
        if(!Double.isFinite(time)) time = 0;
        var entry = entry(owner, ctx, model);
        renderer.render(entry.shell, light);
        if(palette.layers().isEmpty() || fill == 0) return;
        var modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        var clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(modelView);
        if(!ClipBounds.intersects(clip, bounds.minX - .5D, bounds.minY - .5D, bounds.minZ - .5D,
                bounds.maxX - .5D, bounds.maxY - .5D, bounds.maxZ - .5D)) return;
        var mc = Minecraft.getInstance();
        boolean gui = ctx == ItemDisplayContext.GUI;
        var camera = mc.gameRenderer.getMainCamera();
        var input = FluidMotionInput.capture(modelView, mc.level == null ? new Quaternionf() : camera.rotation(),
                mc.level == null ? Vec3.ZERO : camera.getPosition(), gui, time);
        var frame = entry.dynamics.update(input);
        float rest = entry.dynamics.restProgress();
        boolean changed = !palette.equals(entry.palette);
        var up = frame.up().normalize();
        if(changed){
            entry.palette = palette;
            entry.colors = palette.gradient();
            entry.dyeField = null;
            entry.dynamicMesh = null;
            entry.up.set(up);
            entry.mesh = BandedFluidMesh.build(bounds, up, fill, entry.colors);
            entry.meshTime = entry.colorTime = time;
        }
        boolean moving = frame.mixing() > .002F || Math.abs(frame.waveX()) + Math.abs(frame.waveZ()) > .002F;
        boolean active = rest < 1 && (moving || entry.dynamicMesh != null);
        if(active && (entry.dynamicMesh == null || time - entry.colorTime >= 1) && meshUpdates > 0){
            meshUpdates--;
            if(entry.dyeField == null) entry.dyeField = new DensityFluidField(bounds, fill, palette);
            entry.dyeField.update(frame, time);
            if(entry.dynamicMesh == null || entry.up.distanceSquared(up) > 1.0E-6F){
                var surface = BandedFluidMesh.build(bounds, up, fill, entry.colors);
                entry.dynamicMesh = FluidPreviewMesh.build(surface, entry.faces, bounds, palette, entry.dyeField, entry.dynamicMesh);
                entry.up.set(up);
                entry.meshTime = time;
            }else{
                entry.dynamicMesh.refreshColors();
            }
            entry.colorTime = time;
        }
        if(!active && (entry.dynamicMesh != null || entry.up.distanceSquared(up) > 1.0E-6F)){
            entry.mesh = BandedFluidMesh.build(bounds, up, fill, entry.colors);
            entry.up.set(up);
            entry.dynamicMesh = null;
            entry.dyeField = null;
        }
        // Keep shell and liquid in one depth-writing batch; emissive vertices carry full-bright light
        var vertices = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        pose.pushPose();
        try{
            pose.translate(-.5F, -.5F, -.5F);
            if(active && entry.dynamicMesh != null){
                entry.dynamicMesh.draw(pose.last(), vertices, frame, (float)Math.clamp(time - entry.meshTime, 0, 1),
                        (float)Math.clamp(time - entry.colorTime, 0, 1), rest, emissive, light, overlay);
            }else{
                for(var face : entry.mesh){
                    var uv = entry.faces.get(face.textureFace());
                    if(uv != null) draw(pose.last(), vertices, face, uv, emissive, light, overlay);
                }
            }
        }finally{
            pose.popPose();
        }
    }

    // Retain motion separately for each item owner and display context; only the liquid uses emissive shading
    public void render(K owner, ItemDisplayContext ctx, BakedModel model, WeightedColorGradient colors,
                       boolean emissive, double time, PoseStack pose, MultiBufferSource buffers,
                       PartialItemModelRenderer renderer, int light, int overlay){
        var entry = entry(owner, ctx, model);
        renderer.render(entry.shell, light);
        if(colors.colors().isEmpty() || fill == 0) return;
        var modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        var up = entry.motion.update(FluidSurfaceMotion.localUp(modelView, viewUp(ctx)), time);
        if(!colors.equals(entry.colors) || entry.up.distanceSquared(up) > 1.0E-8F){
            entry.colors = colors;
            entry.up.set(up);
            entry.mesh = BandedFluidMesh.build(bounds, up, fill, colors);
        }
        var vertices = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        pose.pushPose();
        try{
            pose.translate(-.5F, -.5F, -.5F);
            for(var face : entry.mesh){
                var uv = entry.faces.get(face.textureFace());
                if(uv == null) continue;
                draw(pose.last(), vertices, face, uv, emissive, light, overlay);
            }
        }finally{
            pose.popPose();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Vector3f viewUp(ItemDisplayContext ctx){
        if(ctx == ItemDisplayContext.GUI) return new Vector3f(0, -1, 0);
        var up = new Vector3f(0, 1, 0);
        var mc = Minecraft.getInstance();
        if(mc.level != null) new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate().transform(up);
        return up;
    }

    private Entry entry(K owner, ItemDisplayContext ctx, BakedModel model){
        return entry(owner, ctx, model, Minecraft.getInstance().player);
    }

    Entry entry(K owner, ItemDisplayContext ctx, BakedModel model, Object player){
        Map<ItemDisplayContext, Entry> contexts;
        if(ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND){
            if(heldOwner.get() != player){ heldEntries.clear(); heldOwner = new java.lang.ref.WeakReference<>(player); }
            contexts = heldEntries;
        }else{
            contexts = entries.computeIfAbsent(owner, ignored -> new EnumMap<>(ItemDisplayContext.class));
        }
        var entry = contexts.get(ctx);
        if(entry == null || entry.model != model){
            entry = new Entry(model);
            contexts.put(ctx, entry);
        }
        return entry;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer vertices, BandedFluidMesh.Face face, FaceUv uv,
                     boolean emissive, int light, int overlay){
        if(emissive) light = LightTexture.FULL_BRIGHT;
        var points = face.vertices();
        if(points.size() == 4){
            for(var point : points) vertex(pose, vertices, face, uv, point, light, overlay);
            return;
        }
        for(int idx = 1; idx < points.size() - 1; idx++){
            vertex(pose, vertices, face, uv, points.getFirst(), light, overlay);
            vertex(pose, vertices, face, uv, points.get(idx), light, overlay);
            vertex(pose, vertices, face, uv, points.get(idx + 1), light, overlay);
            vertex(pose, vertices, face, uv, points.get(idx + 1), light, overlay);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, BandedFluidMesh.Face face, FaceUv uv,
                               Vec3 point, int light, int overlay){
        Vec3 delta = point.subtract(uv.origin);
        float a = (float)(delta.dot(uv.edgeA) / uv.edgeA.lengthSqr());
        float b = (float)(delta.dot(uv.edgeB) / uv.edgeB.lengthSqr());
        vertices.addVertex(pose.pose(), (float)point.x, (float)point.y, (float)point.z)
                .setColor(0xFF000000 | face.color()).setUv(uv.u + a * uv.duA + b * uv.duB, uv.v + a * uv.dvA + b * uv.dvB)
                .setLight(light).setOverlay(overlay).setNormal(pose, (float)face.normal().x, (float)face.normal().y, (float)face.normal().z);
    }

    final class Entry{
        private final BakedModel model, shell;
        private final FluidSurfaceMotion motion = new FluidSurfaceMotion();
        private final DensityFluidMotion dynamics = new DensityFluidMotion();
        private final Map<Direction, FaceUv> faces = new EnumMap<>(Direction.class);
        private final Vector3f up = new Vector3f();
        private WeightedColorGradient colors;
        private DensityColorPalette palette;
        private FluidPreviewMesh dynamicMesh;
        private DensityFluidField dyeField;
        private double meshTime, colorTime;
        private List<BandedFluidMesh.Face> mesh = List.of();

        private Entry(BakedModel model){
            this.model = model;
            shell = new GradientBakedModel(model, texture, Direction.Axis.Y, (float)bounds.minY, (float)bounds.maxY, EMPTY);
            for(int side = -1; side < Direction.values().length; side++){
                var dir = side < 0 ? null : Direction.values()[side];
                for(var quad : model.getQuads(null, dir, RandomSource.create(42), ModelData.EMPTY, null)){
                    if(texture.equals(quad.getSprite().contents().name())) faces.putIfAbsent(quad.getDirection(), FaceUv.of(quad));
                }
            }
        }
    }

    // Project new side and surface vertices onto the original face's atlas UVs
    record FaceUv(Vec3 origin, Vec3 edgeA, Vec3 edgeB, float u, float v, float duA, float dvA, float duB, float dvB){
        static FaceUv of(BakedQuad quad){
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            Vec3 origin = point(data, 0), a = point(data, stride), b = point(data, stride * 3);
            float u = Float.intBitsToFloat(data[4]), v = Float.intBitsToFloat(data[5]);
            return new FaceUv(origin, a.subtract(origin), b.subtract(origin), u, v,
                    Float.intBitsToFloat(data[stride + 4]) - u, Float.intBitsToFloat(data[stride + 5]) - v,
                    Float.intBitsToFloat(data[stride * 3 + 4]) - u, Float.intBitsToFloat(data[stride * 3 + 5]) - v);
        }

        private static Vec3 point(int[] data, int offset){
            return new Vec3(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2]));
        }
    }
}
