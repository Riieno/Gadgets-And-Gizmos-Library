package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;

// Sample a saved effect per vertex without changing geometry, alpha or texture coordinates
public final class ColorEffectVertexConsumer implements VertexConsumer{
    @FunctionalInterface
    public interface Progress{ float sample(float x, float y, float z); }

    private final VertexConsumer delegate;
    private final ColorEffectPreset preset;
    private final double time;
    private final Progress progress;
    private final boolean shaderTint;
    private final boolean textureProgress;
    private int tint;
    private boolean hasColor;
    private boolean hasUv;

    public ColorEffectVertexConsumer(VertexConsumer delegate, ColorEffectPreset preset, double time,
                                    Progress progress, boolean shaderTint){
        this(delegate, preset, time, progress, shaderTint, false);
    }

    public ColorEffectVertexConsumer(VertexConsumer delegate, ColorEffectPreset preset, double time,
                                    Progress progress, boolean shaderTint, boolean textureProgress){
        this.delegate = delegate;
        this.preset = preset;
        this.time = time;
        this.progress = progress;
        this.shaderTint = shaderTint;
        this.textureProgress = textureProgress;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z){
        tint = preset.sample(time, progress.sample(x, y, z), 0);
        hasColor = false;
        hasUv = false;
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){
        if(!shaderTint && preset.active()){
            float brightness = (.2126F * red + .7152F * green + .0722F * blue) / 255F;
            delegate.setColor(Math.round((tint >> 16 & 255) * brightness), Math.round((tint >> 8 & 255) * brightness),
                    Math.round((tint & 255) * brightness), alpha);
        }else delegate.setColor(red, green, blue, alpha);
        hasColor = true;
        if(shaderTint && (!textureProgress || hasUv)) writeTint();
        return this;
    }

    @Override public VertexConsumer setUv(float u, float v){
        if(textureProgress) tint = preset.sample(time, v, 0);
        delegate.setUv(u, v);
        hasUv = true;
        if(shaderTint && textureProgress && hasColor) writeTint();
        return this;
    }
    @Override public VertexConsumer setUv1(int u, int v){ delegate.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v){ delegate.setUv2(u, v); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z){
        if(!shaderTint) delegate.setNormal(x, y, z);
        return this;
    }

    private void writeTint(){
        if(preset.active()) delegate.setNormal((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F);
        else delegate.setNormal(-1, -1, -1);
    }
}
