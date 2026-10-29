package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradientBakedModelTest{
    private static final WeightedColorGradient GRADIENT = new WeightedColorGradient(List.of(
            new WeightedColorGradient.Color(0xFF0000, 3), new WeightedColorGradient.Color(0x0000FF, 1)));

    @Test
    void itemTransformsAndRenderPassesRetainTheGradientWrapper(){
        var base = (BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> switch(method.getName()){
                    case "getRenderPasses" -> List.of(proxy);
                    case "applyTransform" -> proxy;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var model = new GradientBakedModel(base, ResourceLocation.parse("test:liquid"), Direction.Axis.Z, 0, 1, GRADIENT);
        assertSame(model, model.getRenderPasses(null, false).getFirst());
        assertSame(model, model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false));
    }

    @Test
    void slicesAtExactSharesWithoutChangingUvsWindingOrSourceVertices(){
        var model = model(GRADIENT);
        var src = face(new float[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}});
        int[] original = src.getVertices().clone();
        var quads = model.slice(src);
        assertEquals(4, quads.size());
        float[] cuts = {0, .375F, .75F, .875F, 1};
        float area = 0;
        for(int idx = 0; idx < quads.size(); idx++){
            var quad = quads.get(idx);
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            int[] data = quad.getVertices();
            for(int vertex = 0; vertex < 4; vertex++){
                int offset = vertex * 8;
                float y = Float.intBitsToFloat(data[offset + 1]), z = Float.intBitsToFloat(data[offset + 2]);
                min = Math.min(min, z);
                max = Math.max(max, z);
                assertEquals(z, Float.intBitsToFloat(data[offset + 4]), 1.0E-6F);
                assertEquals(y, Float.intBitsToFloat(data[offset + 5]), 1.0E-6F);
                assertEquals(0x80, data[offset + 3] >>> 24);
                assertEquals(0x00F000F0, data[offset + 6]);
                assertEquals(0x0000007F, data[offset + 7]);
            }
            assertEquals(cuts[idx], min, 1.0E-6F);
            assertEquals(cuts[idx + 1], max, 1.0E-6F);
            assertTrue(signedArea(data) < 0);
            area += -signedArea(data);
            assertEquals(src.getDirection(), quad.getDirection());
            assertEquals(src.getTintIndex(), quad.getTintIndex());
            assertEquals(src.isShade(), quad.isShade());
            assertEquals(src.hasAmbientOcclusion(), quad.hasAmbientOcclusion());
        }
        assertEquals(1, area, 1.0E-6F);
        assertArrayEquals(original, src.getVertices());
        assertTrue(hasColor(quads, .375F, 0x800000FF));
        assertTrue(hasColor(quads, .75F, 0x80800080));
        assertTrue(hasColor(quads, .875F, 0x80FF0000));
    }

    @Test
    void emptyStorageHidesLiquidAndEndFacesUseTheEndpointColors(){
        var front = face(new float[][]{{0, 1, 0}, {0, 0, 0}, {1, 0, 0}, {1, 1, 0}});
        assertTrue(model(new WeightedColorGradient(List.of())).slice(front).isEmpty());
        var tinted = model(GRADIENT).slice(front);
        assertEquals(1, tinted.size());
        assertEquals(0x800000FF, tinted.getFirst().getVertices()[3]);
        var back = face(new float[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}});
        assertEquals(0x80FF0000, model(GRADIENT).slice(back).getFirst().getVertices()[3]);
    }

    private static GradientBakedModel model(WeightedColorGradient gradient){
        return new GradientBakedModel(null, ResourceLocation.parse("test:liquid"), Direction.Axis.Z, 0, 1, gradient);
    }

    private static BakedQuad face(float[][] points){
        int[] data = new int[32];
        for(int idx = 0; idx < 4; idx++){
            for(int axis = 0; axis < 3; axis++) data[idx * 8 + axis] = Float.floatToRawIntBits(points[idx][axis]);
            data[idx * 8 + 3] = 0x80FFFFFF;
            data[idx * 8 + 4] = Float.floatToRawIntBits(points[idx][2]);
            data[idx * 8 + 5] = Float.floatToRawIntBits(points[idx][1]);
            data[idx * 8 + 6] = 0x00F000F0;
            data[idx * 8 + 7] = 0x0000007F;
        }
        return new BakedQuad(data, -1, Direction.WEST, null, true, true);
    }

    private static float signedArea(int[] data){
        float area = 0;
        for(int idx = 0; idx < 4; idx++){
            int next = (idx + 1) % 4;
            area += Float.intBitsToFloat(data[idx * 8 + 1]) * Float.intBitsToFloat(data[next * 8 + 2])
                    - Float.intBitsToFloat(data[next * 8 + 1]) * Float.intBitsToFloat(data[idx * 8 + 2]);
        }
        return area * .5F;
    }

    private static boolean hasColor(List<BakedQuad> quads, float z, int color){
        for(var quad : quads){
            int[] data = quad.getVertices();
            for(int idx = 0; idx < 4; idx++){
                if(Math.abs(Float.intBitsToFloat(data[idx * 8 + 2]) - z) < 1.0E-6F && data[idx * 8 + 3] == color) return true;
            }
        }
        return false;
    }
}
