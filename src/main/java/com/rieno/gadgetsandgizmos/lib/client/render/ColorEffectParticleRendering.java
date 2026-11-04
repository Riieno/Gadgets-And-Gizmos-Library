package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectParticles;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.util.Map;
import java.util.WeakHashMap;

// Attach client effects to native particles so their motion, collision and Sable hooks remain active
public final class ColorEffectParticleRendering{
    private static final Map<Particle, ColorEffectParticles.Options> EFFECTS = new WeakHashMap<>();
    private static final VertexFormat FORMAT = ColorEffectShader.withTint(DefaultVertexFormat.PARTICLE);
    private static ShaderInstance shader;
    private static final ParticleRenderType ALPHA = renderType(false);
    private static final ParticleRenderType ADDITIVE = renderType(true);

    private ColorEffectParticleRendering(){}

    public static void register(IEventBus bus){
        ParticleRenderOrdering.registerAfterClouds(ALPHA);
        ParticleRenderOrdering.registerAfterClouds(ADDITIVE);
        bus.addListener((RegisterParticleProvidersEvent evt) -> evt.registerSpecial(ColorEffectParticles.TYPE.get(),
                (opts, level, x, y, z, xd, yd, zd) -> {
                    Particle particle = Minecraft.getInstance().particleEngine.createParticle(opts.original(), x, y, z, xd, yd, zd);
                    if(particle != null) EFFECTS.put(particle, opts);
                    return null;
                }));
        bus.addListener((RegisterShadersEvent evt) -> {
            try{
                evt.registerShader(ColorEffectShader.create(evt.getResourceProvider(), ResourceLocation.withDefaultNamespace("particle"),
                        ResourceLocation.fromNamespaceAndPath(GadgetsNGizmosLibrary.MOD_ID, "color_effect_particle"), FORMAT), val -> shader = val);
            }catch(java.io.IOException err){ throw new IllegalStateException("Failed to load color effect particle shader", err); }
        });
    }

    public static ParticleRenderType renderType(Particle particle){
        var opts = EFFECTS.get(particle);
        if(opts == null || !ColorEffectParticles.enabled(opts.group())) return null;
        return opts.preset().additive() ? ADDITIVE : ALPHA;
    }

    public static VertexConsumer wrap(Particle particle, VertexConsumer buffer, int age, int lifetime, float partialTick){
        var opts = EFFECTS.get(particle);
        if(opts == null) return buffer;
        var level = Minecraft.getInstance().level;
        double time = level == null ? age + partialTick : level.getGameTime() + partialTick;
        var preset = ColorEffectParticles.enabled(opts.group()) ? opts.preset() : ColorEffectPreset.DEFAULT;
        float progress = Math.clamp((age + partialTick) / Math.max(1, lifetime), 0, 1);
        return new ColorEffectVertexConsumer(buffer, preset, time, (x, y, z) -> progress, true);
    }

    private static ParticleRenderType renderType(boolean additive){
        return new ParticleRenderType(){
            @Override public BufferBuilder begin(Tesselator tesselator, TextureManager textures){
                RenderSystem.depthMask(true);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                RenderSystem.enableBlend();
                if(additive) RenderSystem.blendFuncSeparate(770, 1, 1, 1);
                else RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(() -> shader);
                return tesselator.begin(VertexFormat.Mode.QUADS, FORMAT);
            }

            @Override public String toString(){ return "gadgetsngizmos_color_effect_particle_" + additive; }
        };
    }
}
