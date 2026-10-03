package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.io.IOException;
import java.util.function.BooleanSupplier;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL30;

// Provide a reusable textured particle layer with scene depth fading
public final class SoftParticleRenderTypes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation SOFT_BILLBOARD_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "soft_billboard");
    private static final ResourceLocation PARTICLE_DEPTH_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "particle_depth");
    private static final ResourceLocation SOFT_BILLBOARD_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "textures/particle/soft_billboard.png");
    private static final ResourceLocation METABALL_BILLBOARD_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "textures/particle/metaball_billboard.png");
    private static final ResourceLocation STREAK_BILLBOARD_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "textures/particle/plume_streak.png");
    private static final float EMISSIVE_COVERAGE_SCALE = 1.5F;
    private static final ParticleRenderType SOFT_BILLBOARD = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            return beginBillboard(tesselator, false, false);
        }

        // Name the soft billboard layer
        @Override
        public String toString() {
            return "GADGETSNGIZMOS_SOFT_BILLBOARD";
        }
    };

    private static final ParticleRenderType EMISSIVE_BILLBOARD = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            return beginBillboard(tesselator, true, false);
        }

        @Override
        public String toString() {
            return "GADGETSNGIZMOS_EMISSIVE_BILLBOARD";
        }
    };

    private static final ParticleRenderType METABALL_BILLBOARD = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            return beginBillboard(tesselator, false, true);
        }

        @Override
        public String toString() {
            return "GADGETSNGIZMOS_METABALL_BILLBOARD";
        }
    };

    private static final ParticleRenderType EMISSIVE_METABALL_BILLBOARD = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            return beginBillboard(tesselator, true, true);
        }

        @Override
        public String toString() {
            return "GADGETSNGIZMOS_EMISSIVE_METABALL_BILLBOARD";
        }
    };
    private static final ParticleRenderType FAST_BILLBOARD = fastBillboard(
            SOFT_BILLBOARD_TEXTURE_ID, false, "GADGETSNGIZMOS_FAST_BILLBOARD");
    private static final ParticleRenderType FAST_EMISSIVE_BILLBOARD = fastBillboard(
            SOFT_BILLBOARD_TEXTURE_ID, true, "GADGETSNGIZMOS_FAST_EMISSIVE_BILLBOARD");
    private static final ParticleRenderType FAST_METABALL_BILLBOARD = fastBillboard(
            METABALL_BILLBOARD_TEXTURE_ID, false, "GADGETSNGIZMOS_FAST_METABALL_BILLBOARD");
    private static final ParticleRenderType FAST_EMISSIVE_METABALL_BILLBOARD = fastBillboard(
            METABALL_BILLBOARD_TEXTURE_ID, true, "GADGETSNGIZMOS_FAST_EMISSIVE_METABALL_BILLBOARD", true);
    private static final ParticleRenderType FAST_STREAK_BILLBOARD = fastBillboard(
            STREAK_BILLBOARD_TEXTURE_ID, false, "GADGETSNGIZMOS_FAST_STREAK_BILLBOARD");
    private static final ParticleRenderType FAST_EMISSIVE_STREAK_BILLBOARD = fastBillboard(
            STREAK_BILLBOARD_TEXTURE_ID, true, "GADGETSNGIZMOS_FAST_EMISSIVE_STREAK_BILLBOARD", true);
    private static final ParticleRenderType EARLY_TRANSLUCENT_PARTICLE_SHEET = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public boolean isTranslucent() {
            return false;
        }

        @Override
        public String toString() {
            return "GADGETSNGIZMOS_EARLY_TRANSLUCENT_PARTICLE_SHEET";
        }
    };

    // Scene depth snapshot shared by the particle batch
    private static TextureTarget depthCopy;
    // Shader registered by the library client bootstrap
    private static ShaderInstance shader;
    // Lightweight shader for the particle depth pass
    private static ShaderInstance depthShader;
    // Optional host probe for a running shader pack
    private static BooleanSupplier shaderPackActive = () -> false;
    // Optional host binder for a shader pack's active world target
    private static BooleanSupplier shaderPackWorldTargetBinder;

    // Initialize the soft particle render types
    private SoftParticleRenderTypes() {
    }

    // Get the procedural soft billboard layer
    public static ParticleRenderType softBillboard() {
        return SOFT_BILLBOARD;
    }

    // Get the additive billboard layer for colored emissive particles
    public static ParticleRenderType emissiveBillboard() {
        return EMISSIVE_BILLBOARD;
    }

    // Get the animated metaball billboard layer
    public static ParticleRenderType metaballBillboard() {
        return METABALL_BILLBOARD;
    }

    // Get the additive animated metaball billboard layer
    public static ParticleRenderType emissiveMetaballBillboard() {
        return EMISSIVE_METABALL_BILLBOARD;
    }

    // Get the soft billboard without a scene depth copy
    public static ParticleRenderType fastBillboard() {
        return FAST_BILLBOARD;
    }

    // Get the additive soft billboard without a scene depth copy
    public static ParticleRenderType fastEmissiveBillboard() {
        return FAST_EMISSIVE_BILLBOARD;
    }

    // Get the animated metaball without a scene depth copy
    public static ParticleRenderType fastMetaballBillboard() {
        return FAST_METABALL_BILLBOARD;
    }

    // Get the additive animated metaball without a scene depth copy
    public static ParticleRenderType fastEmissiveMetaballBillboard() {
        return FAST_EMISSIVE_METABALL_BILLBOARD;
    }

    // Get the elongated streak without a scene depth copy
    public static ParticleRenderType fastStreakBillboard() {
        return FAST_STREAK_BILLBOARD;
    }

    // Get the additive elongated streak without a scene depth copy
    public static ParticleRenderType fastEmissiveStreakBillboard() {
        return FAST_EMISSIVE_STREAK_BILLBOARD;
    }

    // Draw emissive plume color before writing its visible scene depth
    public static void drawEmissiveBatch(ParticleRenderType type, Runnable firstDraw) {
        if (type != FAST_EMISSIVE_METABALL_BILLBOARD && type != FAST_EMISSIVE_STREAK_BILLBOARD) {
            firstDraw.run();
            return;
        }
        RenderTarget particles = Minecraft.getInstance().levelRenderer.getParticlesTarget();
        boolean separateTarget = particles != null
                && GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == particles.frameBufferId;
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        boolean shaderPack = shaderPackActive.getAsBoolean();
        float coverageScale = EMISSIVE_COVERAGE_SCALE;
        float coverageAlpha = shaderColor[3] * coverageScale;
        try {
            if (shaderPack) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE);
                firstDraw.run();
                drawEmissiveDepth();
                return;
            }
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], coverageAlpha);
            RenderSystem.colorMask(!separateTarget, !separateTarget, !separateTarget, separateTarget);
            if (separateTarget) {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            } else {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            firstDraw.run();

            RenderSystem.setShaderColor(shaderColor[0] / coverageScale,
                    shaderColor[1] / coverageScale,
                    shaderColor[2] / coverageScale, coverageAlpha);
            RenderSystem.colorMask(true, true, true, !separateTarget);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            DefaultVertexFormat.PARTICLE.getImmediateDrawVertexBuffer().drawWithShader(
                    RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            drawEmissiveDepth();
        } finally {
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
        }
    }

    // Store visible particle depth after all colors in the batch have blended
    private static void drawEmissiveDepth() {
        boolean mirrorWorldDepth = shaderPackActive.getAsBoolean() && shaderPackWorldTargetBinder != null;
        int drawTarget = mirrorWorldDepth ? GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;
        int readTarget = mirrorWorldDepth ? GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : 0;
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(true);
        ShaderInstance previousShader = RenderSystem.getShader();
        if (depthShader != null) RenderSystem.setShader(() -> depthShader);
        try {
            DefaultVertexFormat.PARTICLE.getImmediateDrawVertexBuffer().drawWithShader(
                    RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            if (mirrorWorldDepth && bindShaderPackWorldTarget()
                    && GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != drawTarget) {
                DefaultVertexFormat.PARTICLE.getImmediateDrawVertexBuffer().drawWithShader(
                        RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            }
        } finally {
            if (mirrorWorldDepth) {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            }
            if (depthShader != null) RenderSystem.setShader(() -> previousShader);
        }
    }

    // Render particle-atlas smoke before translucent blocks
    public static ParticleRenderType earlyTranslucentParticleSheet() {
        return EARLY_TRANSLUCENT_PARTICLE_SHEET;
    }

    // Create a textured particle layer that does not copy the scene depth
    private static ParticleRenderType fastBillboard(ResourceLocation texture, boolean emissive, String name) {
        return fastBillboard(texture, emissive, name, false);
    }

    // Place selected particles ahead of translucent block rendering
    private static ParticleRenderType fastBillboard(ResourceLocation texture, boolean emissive,
            String name, boolean beforeTranslucent) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                return beginBillboard(tesselator, emissive, texture, true);
            }

            @Override
            public String toString() {
                return name;
            }

            @Override
            public boolean isTranslucent() {
                return !beforeTranslucent;
            }
        };
    }

    // Prepare the scene depth and particle blend mode
    private static BufferBuilder beginBillboard(Tesselator tesselator, boolean emissive, boolean metaball) {
        ResourceLocation texture = metaball ? METABALL_BILLBOARD_TEXTURE_ID : SOFT_BILLBOARD_TEXTURE_ID;
        return beginBillboard(tesselator, emissive, texture, false);
    }

    // Prepare the selected texture and blend mode
    private static BufferBuilder beginBillboard(Tesselator tesselator, boolean emissive,
            ResourceLocation texture, boolean fast) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        boolean useTexture = fast || shader == null || shaderPackActive.getAsBoolean() || main == null
                || main.width <= 0 || main.height <= 0 || main.getDepthTextureId() <= 0
                || GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId;
        if (useTexture) {
            // Keep shader pack particle passes on their active framebuffer
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, texture);
        } else {
            if (depthCopy == null) {
                depthCopy = new TextureTarget(main.width, main.height, true, false);
            } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
                depthCopy.resize(main.width, main.height, false);
            }
            depthCopy.copyDepthFrom(main);
            main.bindWrite(false);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderTexture(3, depthCopy.getDepthTextureId());
            shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
            shader.safeGetUniform("HasSceneDepth").set(1);
            shader.safeGetUniform("EmissionStrength").set(emissive ? 0.85F : 0.0F);
            RenderSystem.setShader(() -> shader);
        }
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        if (emissive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    // Set the host's active shader pack probe
    public static void setShaderPackActiveSupplier(BooleanSupplier supplier) {
        shaderPackActive = supplier == null ? () -> false : supplier;
    }

    // Check whether the host is drawing through a shader pack
    public static boolean isShaderPackActive() {
        return shaderPackActive.getAsBoolean();
    }

    // Set the host's active shader world target binder
    public static void setShaderPackWorldTargetBinder(BooleanSupplier binder) {
        shaderPackWorldTargetBinder = binder;
    }

    // Check whether late particles can use the host's shader world target
    public static boolean canBindShaderPackWorldTarget() {
        return shaderPackWorldTargetBinder != null;
    }

    // Bind the host's active shader world target
    public static boolean bindShaderPackWorldTarget() {
        return shaderPackWorldTargetBinder != null && shaderPackWorldTargetBinder.getAsBoolean();
    }

    // Register the shared particle shader
    public static void onRegisterShaders(RegisterShadersEvent evt) throws IOException {
        evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SOFT_BILLBOARD_SHADER_ID,
                DefaultVertexFormat.PARTICLE), registered -> shader = registered);
        evt.registerShader(new ShaderInstance(evt.getResourceProvider(), PARTICLE_DEPTH_SHADER_ID,
                DefaultVertexFormat.PARTICLE), registered -> depthShader = registered);
    }
}
