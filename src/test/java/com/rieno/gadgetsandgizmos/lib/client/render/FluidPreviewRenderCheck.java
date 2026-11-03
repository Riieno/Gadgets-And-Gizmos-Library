package com.rieno.gadgetsandgizmos.lib.client.render;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Export the actual preview vertices for a repeatable camera movement and settling visual check
public final class FluidPreviewRenderCheck{
    private record Snapshot(int tick, float mixing, List<float[]> vertices, List<Integer> colors, List<Integer> lights){}

    public static void main(String[] args) throws Exception{
        var bounds = new AABB(0, 0, 0, 1, 1, 2.4D);
        var palette = new DensityColorPalette(List.of(new DensityColorPalette.Layer(0xEBC641, 1, 4),
                new DensityColorPalette.Layer(0x3456BD, 1, 11), new DensityColorPalette.Layer(0xC34735, 1, 14)));
        var motion = new DensityFluidMotion();
        var field = new DensityFluidField(bounds, .85F, palette);
        var pose = new Matrix4f().translation(.4F, -.3F, -.8F);
        var snapshots = new ArrayList<Snapshot>();
        FluidPreviewMesh mesh = null;
        for(int tick = 0; tick <= 420; tick++){
            float yaw = tick >= 20 && tick < 80 ? (float)Math.sin((tick - 20) * .16D) * .6F : 0;
            double x = tick >= 20 && tick < 80 ? Math.sin((tick - 20) * .2D) * .4D : 0;
            var frame = motion.update(FluidMotionInput.capture(pose, new Quaternionf().rotateY(yaw), new Vec3(x, 80, 0), false, tick));
            field.update(frame, tick);
            boolean active = motion.restProgress() < 1;
            var faces = BandedFluidMesh.build(bounds, frame.up().normalize(), .85F, palette.gradient());
            var sink = new Sink();
            var textures = FluidPreviewMeshTest.textures();
            if(active){
                mesh = FluidPreviewMesh.build(faces, textures, bounds, palette, field, mesh);
                mesh.draw(new PoseStack().last(), sink, frame, 1, 1, motion.restProgress(), true, 0, 0);
            }else{
                mesh = null;
                for(var face : faces) ItemFluidPreview.draw(new PoseStack().last(), sink, face, textures.get(face.textureFace()), true, 0, 0);
            }
            if(tick % 5 == 0 || tick >= 79 && tick <= 101) snapshots.add(new Snapshot(tick, frame.mixing(), sink.vertices, sink.colors, sink.lights));
        }
        var output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new Gson().toJson(snapshots));
        System.out.println("Exported " + snapshots.size() + " fluid render snapshots to " + output);
    }

    private static final class Sink implements VertexConsumer{
        private final List<float[]> vertices = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private final List<Integer> lights = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z){ vertices.add(new float[]{x, y, z}); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){ colors.add(alpha << 24 | red << 16 | green << 8 | blue); return this; }
        @Override public VertexConsumer setUv(float u, float v){ return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ lights.add(u | v << 16); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
