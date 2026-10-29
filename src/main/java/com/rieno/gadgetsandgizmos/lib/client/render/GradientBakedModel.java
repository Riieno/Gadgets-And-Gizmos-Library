package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Tint selected model faces with weighted color bands while retaining their texture and shape
public final class GradientBakedModel extends BakedModelWrapper<BakedModel>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ResourceLocation texture;
    private final int axis;
    private final float start, length;
    private final WeightedColorGradient gradient;
    private final List<Double> cuts;
    private final Map<BakedQuad, List<BakedQuad>> quads = new IdentityHashMap<>();
    private final Map<BakedModel, GradientBakedModel> passes = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Use baked model coordinates before item centering; an empty gradient hides the selected faces
    public GradientBakedModel(BakedModel model, ResourceLocation texture, Direction.Axis axis,
                              float start, float end, WeightedColorGradient gradient){
        super(model);
        if(!Float.isFinite(start) || !Float.isFinite(end) || !Float.isFinite(end - start) || end <= start){
            throw new IllegalArgumentException("Gradient bounds must be finite and increasing");
        }
        this.texture = texture;
        this.axis = switch(axis){ case X -> 0; case Y -> 1; case Z -> 2; };
        this.start = start;
        length = end - start;
        this.gradient = gradient;
        var cuts = new ArrayList<Double>();
        cuts.add(0D);
        for(var band : gradient.bands()){
            cuts.add((band.start() + band.end()) * .5D);
            cuts.add(band.end());
        }
        this.cuts = List.copyOf(cuts);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random){
        return tint(originalModel.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData data, @Nullable RenderType layer){
        return tint(originalModel.getQuads(state, side, random, data, layer));
    }

    // Keep the wrapper active when an item renderer requests separate model passes
    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous){
        var res = new ArrayList<BakedModel>();
        for(var pass : originalModel.getRenderPasses(stack, fabulous)){
            res.add(wrap(pass));
        }
        return res;
    }

    // Preserve the gradient when the normal item pipeline applies display transforms
    @Override
    public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand){
        return wrap(originalModel.applyTransform(ctx, pose, leftHand));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private BakedModel wrap(BakedModel model){
        return model == originalModel ? this : passes.computeIfAbsent(model, val ->
                new GradientBakedModel(val, texture, Direction.Axis.values()[axis], start, start + length, gradient));
    }

    private List<BakedQuad> tint(List<BakedQuad> src){
        var res = new ArrayList<BakedQuad>();
        for(var quad : src){
            if(!texture.equals(quad.getSprite().contents().name())){
                res.add(quad);
            }else{
                res.addAll(quads.computeIfAbsent(quad, this::slice));
            }
        }
        return res;
    }

    List<BakedQuad> slice(BakedQuad quad){
        if(gradient.colors().isEmpty()) return List.of();
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        var points = new ArrayList<int[]>(4);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for(int idx = 0; idx < 4; idx++){
            int[] point = new int[stride];
            System.arraycopy(data, idx * stride, point, 0, stride);
            points.add(point);
            float val = Float.intBitsToFloat(point[axis]);
            min = Math.min(min, val);
            max = Math.max(max, val);
        }
        var res = new ArrayList<BakedQuad>();
        if(max - min < 1.0E-7F){
            emit(res, quad, points);
        }else{
            for(int idx = 1; idx < cuts.size(); idx++){
                float low = idx == 1 ? min : Math.max(min, start + length * cuts.get(idx - 1).floatValue());
                float high = idx == cuts.size() - 1 ? max : Math.min(max, start + length * cuts.get(idx).floatValue());
                if(high <= low) continue;
                emit(res, quad, clip(clip(points, low, true), high, false));
            }
        }
        return List.copyOf(res);
    }

    // Clip each face at the band boundaries and centers without stretching its UVs
    private List<int[]> clip(List<int[]> points, float plane, boolean above){
        if(points.isEmpty()) return points;
        var res = new ArrayList<int[]>();
        int[] prev = points.getLast();
        float prevPos = Float.intBitsToFloat(prev[axis]);
        boolean prevInside = above ? prevPos >= plane : prevPos <= plane;
        for(int[] point : points){
            float pos = Float.intBitsToFloat(point[axis]);
            boolean inside = above ? pos >= plane : pos <= plane;
            if(inside != prevInside){
                float ratio = (plane - prevPos) / (pos - prevPos);
                int[] crossing = prev.clone();
                for(int idx = 0; idx <= 5; idx++){
                    if(idx == 3) continue;
                    float from = Float.intBitsToFloat(prev[idx]), to = Float.intBitsToFloat(point[idx]);
                    crossing[idx] = Float.floatToRawIntBits(from + (to - from) * ratio);
                }
                crossing[axis] = Float.floatToRawIntBits(plane);
                crossing[3] = blendColor(prev[3], point[3], ratio);
                res.add(crossing);
            }
            if(inside) res.add(point);
            prev = point;
            prevPos = pos;
            prevInside = inside;
        }
        return res;
    }

    private void emit(List<BakedQuad> res, BakedQuad src, List<int[]> points){
        if(points.size() < 3) return;
        if(points.size() == 4){
            res.add(quad(src, points.get(0), points.get(1), points.get(2), points.get(3)));
            return;
        }
        for(int idx = 1; idx < points.size() - 1; idx++){
            res.add(quad(src, points.get(0), points.get(idx), points.get(idx + 1), points.get(idx + 1)));
        }
    }

    private BakedQuad quad(BakedQuad src, int[]... points){
        int stride = points[0].length;
        int[] data = new int[stride * 4];
        for(int idx = 0; idx < 4; idx++){
            System.arraycopy(points[idx], 0, data, idx * stride, stride);
            int color = gradient.sample((Float.intBitsToFloat(points[idx][axis]) - start) / length);
            int original = points[idx][3];
            data[idx * stride + 3] = original & 0xFF000000
                    | (original & 255) * (color >> 16 & 255) / 255
                    | (original >> 8 & 255) * (color >> 8 & 255) / 255 << 8
                    | (original >> 16 & 255) * (color & 255) / 255 << 16;
        }
        return new BakedQuad(data, src.getTintIndex(), src.getDirection(), src.getSprite(), src.isShade(), src.hasAmbientOcclusion());
    }

    private static int blendColor(int from, int to, float ratio){
        int res = 0;
        for(int shift = 0; shift < 32; shift += 8){
            int val = from >>> shift & 255;
            res |= Math.round(val + ((to >>> shift & 255) - val) * ratio) << shift;
        }
        return res;
    }
}
