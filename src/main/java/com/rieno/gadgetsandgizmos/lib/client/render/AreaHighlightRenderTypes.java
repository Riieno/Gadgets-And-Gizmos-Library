package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.io.IOException;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// Provide the shared render type used by translucent area highlights
public final class AreaHighlightRenderTypes extends RenderStateShard {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation AREA_HIGHLIGHT_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID,
            "area_highlight"
    );
    private static final RenderStateShard.ShaderStateShard AREA_HIGHLIGHT_SHADER =
            new RenderStateShard.ShaderStateShard(() -> Shaders.areaHighlightShader);
    private static final RenderType CLAW_MARKER = RenderType.create(
            createLayerName("claw_marker"),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(AREA_HIGHLIGHT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(PonderSpecialTextures.BLANK.getLocation(), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the area highlight render types
    private AreaHighlightRenderTypes() {
        super("area_highlight", () -> {}, () -> {});
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the claw marker
    public static RenderType clawMarker() {
        return CLAW_MARKER;
    }

    // Create the layer name
    private static String createLayerName(String name) {
        return GadgetsNGizmosLibrary.MOD_ID + ":" + name;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the register shaders event
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ResourceProvider resourceProvider = event.getResourceProvider();
        event.registerShader(new ShaderInstance(resourceProvider, AREA_HIGHLIGHT_SHADER_ID, DefaultVertexFormat.NEW_ENTITY), shader -> Shaders.areaHighlightShader = shader);
    }

    // Load the area highlight shaders
    private static final class Shaders {
        // Shared area highlight shader
        private static ShaderInstance areaHighlightShader;

        // Initialize the shaders
        private Shaders() {
        }
    }
}
