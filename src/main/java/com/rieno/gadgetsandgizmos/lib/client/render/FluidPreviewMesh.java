package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.ColorGradient;
import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import com.rieno.gadgetsandgizmos.lib.geometry.BandedFluidMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Cache texture projections and wave weights so rendering needs no clipping or trigonometry
final class FluidPreviewMesh{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int STRIDE = 17;
    private record Key(int color, Direction face, boolean surface){}
    private record Patch(BandedFluidMesh.Face face, float[] data, int mixed, float mobility, int[] colors, int[] prevColors){}
    private final Map<Key, Patch> patches;
    private final AABB bounds;
    private final DensityFluidField dyeField;

    private FluidPreviewMesh(Map<Key, Patch> patches, AABB bounds, DensityFluidField dyeField){
        this.patches = patches;
        this.bounds = bounds;
        this.dyeField = dyeField;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static FluidPreviewMesh build(List<BandedFluidMesh.Face> faces, Map<Direction, ItemFluidPreview.FaceUv> textures,
                                  AABB bounds, DensityColorPalette palette, FluidPreviewMesh prev){
        return build(faces, textures, bounds, palette, null, prev);
    }

    static FluidPreviewMesh build(List<BandedFluidMesh.Face> faces, Map<Direction, ItemFluidPreview.FaceUv> textures,
                                  AABB bounds, DensityColorPalette palette, DensityFluidField dyeField, FluidPreviewMesh prev){
        var patches = new HashMap<Key, Patch>();
        for(var face : faces){
            var uv = textures.get(face.textureFace());
            if(uv == null) continue;
            var key = new Key(face.color(), face.textureFace(), surface(face, bounds));
            var points = face.vertices();
            float[] data = pack(tessellate(points, dyeField != null, bounds), uv, face.normal(), bounds);
            int[] colors = dyeField == null ? null : new int[data.length / STRIDE];
            int[] prevColors = colors == null ? null : new int[colors.length];
            if(colors != null) for(int idx = 0; idx < colors.length; idx++){
                colors[idx] = dyeField.sampleSurface(data[idx * STRIDE], data[idx * STRIDE + 1], data[idx * STRIDE + 2]);
                prevColors[idx] = colors[idx];
            }
            patches.put(key, new Patch(new BandedFluidMesh.Face(points, face.normal(), face.textureFace(), face.color()), data,
                    palette.mixedColor(face.color()), palette.mobility(face.color()), colors, prevColors));
        }
        return new FluidPreviewMesh(patches, bounds, dyeField);
    }

    void refreshColors(){
        if(dyeField == null) return;
        for(var patch : patches.values()) for(int idx = 0; idx < patch.colors.length; idx++){
            int offset = idx * STRIDE;
            patch.prevColors[idx] = patch.colors[idx];
            patch.colors[idx] = dyeField.sampleSurface(patch.data[offset], patch.data[offset + 1], patch.data[offset + 2]);
        }
    }

    void draw(PoseStack.Pose pose, VertexConsumer vertices, DensityFluidMotion.Frame frame, float alpha,
              boolean emissive, int light, int overlay){
        draw(pose, vertices, frame, alpha, alpha, emissive, light, overlay);
    }

    void draw(PoseStack.Pose pose, VertexConsumer vertices, DensityFluidMotion.Frame frame, float alpha, float colorAlpha,
              boolean emissive, int light, int overlay){
        draw(pose, vertices, frame, alpha, colorAlpha, 0, emissive, light, overlay);
    }

    void draw(PoseStack.Pose pose, VertexConsumer vertices, DensityFluidMotion.Frame frame, float alpha, float colorAlpha,
              float rest, boolean emissive, int light, int overlay){
        if(emissive) light = LightTexture.FULL_BRIGHT;
        rest = Math.clamp(rest, 0, 1);
        float settling = rest * rest * (3 - 2 * rest);
        for(var patch : patches.values()){
            float[] data = patch.data;
            for(int idx = 0; idx < data.length; idx += STRIDE){
                float wave = (frame.waveX() * data[idx + 11] + frame.waveZ() * data[idx + 12]) * .65F;
                float x = data[idx] + frame.upX() * wave * data[idx + 13];
                float y = data[idx + 1] + frame.upY() * wave * data[idx + 14];
                float z = data[idx + 2] + frame.upZ() * wave * data[idx + 15];
                int color = patch.colors != null ? ColorGradient.blend(patch.prevColors[idx / STRIDE], patch.colors[idx / STRIDE], colorAlpha)
                        : frame.mixing() > 0 ? ColorGradient.blend(patch.face.color(), patch.mixed,
                        frame.mixing() * patch.mobility * data[idx + 16]) : patch.face.color();
                if(settling > 0) color = ColorGradient.blend(color, patch.face.color(), settling);
                vertices.addVertex(pose.pose(), (float)Math.clamp(x, bounds.minX, bounds.maxX),
                                (float)Math.clamp(y, bounds.minY, bounds.maxY), (float)Math.clamp(z, bounds.minZ, bounds.maxZ))
                        .setColor(0xFF000000 | color).setUv(data[idx + 6], data[idx + 7]).setLight(light).setOverlay(overlay)
                        .setNormal(pose, data[idx + 8], data[idx + 9], data[idx + 10]);
            }
        }
    }

    int vertices(){
        int count = 0;
        for(var patch : patches.values()) count += patch.data.length / STRIDE;
        return count;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static boolean surface(BandedFluidMesh.Face face, AABB bounds){
        var dir = face.textureFace();
        double plane = switch(dir){
            case DOWN -> bounds.minY; case UP -> bounds.maxY; case NORTH -> bounds.minZ;
            case SOUTH -> bounds.maxZ; case WEST -> bounds.minX; case EAST -> bounds.maxX;
        };
        for(var point : face.vertices()){
            double val = switch(dir.getAxis()){ case X -> point.x; case Y -> point.y; case Z -> point.z; };
            if(Math.abs(val - plane) > 1.0E-8D) return true;
        }
        return false;
    }

    // Subdivide faces so shared vertex colours form smooth edges across the liquid surface
    private static List<Vec3> tessellate(List<Vec3> points, boolean volume, AABB bounds){
        var res = new ArrayList<Vec3>();
        if(points.size() == 4){
            int columns = volume ? subdivisions(points.get(0), points.get(1), bounds, 6) : 2;
            int rows = volume ? subdivisions(points.get(0), points.get(3), bounds, 12) : 2;
            for(int row = 0; row < rows; row++) for(int col = 0; col < columns; col++){
                res.add(quadPoint(points, col / (double)columns, row / (double)rows));
                res.add(quadPoint(points, (col + 1D) / columns, row / (double)rows));
                res.add(quadPoint(points, (col + 1D) / columns, (row + 1D) / rows));
                res.add(quadPoint(points, col / (double)columns, (row + 1D) / rows));
            }
        }else{
            for(int idx = 1; idx < points.size() - 1; idx++){
                Vec3 a = points.getFirst(), b = points.get(idx), c = points.get(idx + 1);
                Vec3 ab = a.lerp(b, .5D), bc = b.lerp(c, .5D), ca = c.lerp(a, .5D);
                if(volume){
                    splitTriangle(res, a, ab, ca); splitTriangle(res, ab, b, bc);
                    splitTriangle(res, ca, bc, c); splitTriangle(res, ab, bc, ca);
                }else{
                    triangle(res, a, ab, ca); triangle(res, ab, b, bc);
                    triangle(res, ca, bc, c); triangle(res, ab, bc, ca);
                }
            }
        }
        return res;
    }

    // Allocate subdivisions by face size so adding resting band boundaries keeps the mesh bounded
    private static int subdivisions(Vec3 from, Vec3 to, AABB bounds, int count){
        double x = (to.x - from.x) / bounds.getXsize(), y = (to.y - from.y) / bounds.getYsize();
        double z = (to.z - from.z) / bounds.getZsize();
        return Math.clamp((int)Math.ceil(Math.sqrt(x * x + y * y + z * z) * count), 1, count);
    }

    private static Vec3 quadPoint(List<Vec3> points, double u, double v){
        return points.get(0).lerp(points.get(1), u).lerp(points.get(3).lerp(points.get(2), u), v);
    }

    private static void triangle(List<Vec3> res, Vec3 a, Vec3 b, Vec3 c){ res.add(a); res.add(b); res.add(c); res.add(c); }

    private static void splitTriangle(List<Vec3> res, Vec3 a, Vec3 b, Vec3 c){
        Vec3 ab = a.lerp(b, .5D), bc = b.lerp(c, .5D), ca = c.lerp(a, .5D);
        triangle(res, a, ab, ca); triangle(res, ab, b, bc); triangle(res, ca, bc, c); triangle(res, ab, bc, ca);
    }

    private static float[] pack(List<Vec3> points, ItemFluidPreview.FaceUv uv, Vec3 normal, AABB bounds){
        float[] data = new float[points.size() * STRIDE];
        for(int vertex = 0; vertex < points.size(); vertex++){
            int idx = vertex * STRIDE;
            var point = points.get(vertex);
            var delta = point.subtract(uv.origin());
            float a = (float)(delta.dot(uv.edgeA()) / uv.edgeA().lengthSqr());
            float b = (float)(delta.dot(uv.edgeB()) / uv.edgeB().lengthSqr());
            data[idx] = data[idx + 3] = (float)point.x;
            data[idx + 1] = data[idx + 4] = (float)point.y;
            data[idx + 2] = data[idx + 5] = (float)point.z;
            data[idx + 6] = uv.u() + a * uv.duA() + b * uv.duB();
            data[idx + 7] = uv.v() + a * uv.dvA() + b * uv.dvB();
            data[idx + 8] = (float)normal.x; data[idx + 9] = (float)normal.y; data[idx + 10] = (float)normal.z;
            double x = Math.clamp((point.x - bounds.minX) / bounds.getXsize(), 0, 1);
            double y = Math.clamp((point.y - bounds.minY) / bounds.getYsize(), 0, 1);
            double z = Math.clamp((point.z - bounds.minZ) / bounds.getZsize(), 0, 1);
            data[idx + 11] = (float)(Math.cos(Math.PI * x) * Math.cos(Math.PI * z));
            data[idx + 12] = (float)(Math.sin(Math.PI * x) * Math.sin(Math.PI * z));
            double free = 16 * x * (1 - x) * y * (1 - y) * z * (1 - z);
            data[idx + 13] = (float)(bounds.getXsize() * free);
            data[idx + 14] = (float)(bounds.getYsize() * free);
            data[idx + 15] = (float)(bounds.getZsize() * free);
            data[idx + 16] = .4F + .6F * Math.abs(data[idx + 11]);
        }
        return data;
    }

}
