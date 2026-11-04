package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColorEffectRenderingTest{
    private static ColorEffectPreset gradient(){
        return new ColorEffectPreset(ColorEffectPreset.Mode.GRADIENT, 0xFF0000,
                List.of(new ColorEffectPreset.Stop(0, 0xFF0000), new ColorEffectPreset.Stop(1, 0x0000FF)), false, false);
    }

    @Test
    void spatialGradientPreservesBrightnessAlphaAndGeometry(){
        var sink = new Sink();
        var vertices = new ColorEffectVertexConsumer(sink, gradient(), 0, (x, y, z) -> y, false);
        vertices.addVertex(3, 0, 5).setColor(128, 128, 128, 77).setUv(.2F, .8F).setLight(0x00F000F0);
        assertArrayEquals(new int[]{128, 0, 0, 77}, sink.color);
        assertArrayEquals(new float[]{3, 0, 5}, sink.pos);
        assertArrayEquals(new float[]{.2F, .8F}, sink.uv);
        assertArrayEquals(new int[]{240, 240}, sink.light);
        vertices.addVertex(3, 1, 5).setColor(128, 128, 128, 77);
        assertArrayEquals(new int[]{0, 0, 128, 77}, sink.color);
    }

    @Test
    void shaderTintUsesPlumeCoordinatesAndPreservesNativeStyleColor(){
        var sink = new Sink();
        var vertices = new ColorEffectVertexConsumer(sink, gradient(), 0, (x, y, z) -> 0, true, true);
        vertices.addVertex(4, 8, 9).setUv(.5F, 1).setColor(20, 180, 220, 33);
        assertArrayEquals(new int[]{20, 180, 220, 33}, sink.color);
        assertArrayEquals(new float[]{0, 0, 1}, sink.tint);
        vertices.setNormal(1, 0, 0);
        assertArrayEquals(new float[]{0, 0, 1}, sink.tint);
        vertices.addVertex(4, 8, 9).setColor(20, 180, 220, 33).setUv(.5F, 0);
        assertArrayEquals(new float[]{1, 0, 0}, sink.tint);
    }

    @Test
    void disabledShaderTintRetainsOriginalPixelPath(){
        var sink = new Sink();
        var vertices = new ColorEffectVertexConsumer(sink, ColorEffectPreset.DEFAULT, 0, (x, y, z) -> 0, true);
        vertices.addVertex(0, 0, 0).setColor(20, 50, 70, 99);
        assertArrayEquals(new int[]{20, 50, 70, 99}, sink.color);
        assertArrayEquals(new float[]{-1, -1, -1}, sink.tint);
    }

    @Test
    void shaderWrapAppliesTintAfterNativeReturnsAndKeepsDiscard(){
        String source = "#version 150\nout vec4 fragColor;\nvoid main(){ if(false) discard; fragColor=vec4(1); return; }";
        String transformed = ColorEffectShader.transform(source, ".fsh", ResourceLocation.parse("test:effect"));
        assertTrue(transformed.contains("void colorEffectOriginalMain()"));
        assertTrue(transformed.contains("if(false) discard"));
        assertTrue(transformed.indexOf("colorEffectOriginalMain();") < transformed.indexOf("fragColor.rgb = brightness"));
        assertTrue(transformed.contains("if(colorEffectTint.x >= 0.0)"));
        String json = ColorEffectShader.transform("{\"vertex\":\"particle\",\"fragment\":\"particle\"}", ".json", ResourceLocation.parse("test:effect"));
        assertTrue(json.contains("test:effect"));
    }

    @Test
    void savedPresetDoesNotReplaceNativeDataAndMissingTagClearsTheEffect(){
        var state = new ColorEffectState();
        var tag = new CompoundTag();
        tag.putInt("flameColor", 0x123456);
        tag.put(ColorEffectState.NBT_KEY, gradient().write());
        state.read(tag);
        var saved = new CompoundTag();
        saved.putInt("flameColor", 0x123456);
        state.write(saved);
        assertEquals(gradient(), ColorEffectPreset.read(saved.getCompound(ColorEffectState.NBT_KEY)));
        assertEquals(0x123456, saved.getInt("flameColor"));
        state.read(new CompoundTag());
        assertEquals(ColorEffectPreset.DEFAULT, state.preset());
        state.write(saved);
        assertFalse(saved.contains(ColorEffectState.NBT_KEY));
        assertEquals(0x123456, saved.getInt("flameColor"));
    }

    private static final class Sink implements VertexConsumer{
        private float[] pos, uv, tint;
        private int[] color, light;
        @Override public VertexConsumer addVertex(float x, float y, float z){ pos = new float[]{x, y, z}; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ color = new int[]{r, g, b, a}; return this; }
        @Override public VertexConsumer setUv(float u, float v){ uv = new float[]{u, v}; return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ light = new int[]{u, v}; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ tint = new float[]{x, y, z}; return this; }
    }
}
