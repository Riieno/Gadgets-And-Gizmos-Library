package com.rieno.gadgetsandgizmos.lib.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.AreaHighlightRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.SceneRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.DesaturatedModelRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.SoftParticleRenderTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import com.rieno.gadgetsandgizmos.lib.tooltip.ColorEffectTooltip;
import com.rieno.gadgetsandgizmos.lib.client.tooltip.ClientColorEffectTooltip;

// Register the library's client render resources without loading them on a server
public final class GadgetsNGizmosLibraryClientBootstrap {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Gadgets & Gizmos client bootstrap
    private GadgetsNGizmosLibraryClientBootstrap() {
    }

    // Register the Gadgets & Gizmos client bootstrap
    public static void register(IEventBus modEventBus) {
        com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient.bootstrap();
        modEventBus.addListener((RegisterClientTooltipComponentFactoriesEvent evt) ->
                evt.register(ColorEffectTooltip.class, ClientColorEffectTooltip::new));
        modEventBus.addListener((RegisterShadersEvent evt) -> {
            com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer.clear();
            try{
                DesaturatedModelRenderTypes.registerShader(evt);
            }catch(java.io.IOException err){
                throw new RuntimeException("Failed to register desaturated model shader", err);
            }
            try{
                SceneRenderTypes.registerShader(evt);
            }catch(java.io.IOException err){
                throw new RuntimeException("Failed to register scene texture shader", err);
            }
            try{
                SurfaceFloodlightRenderer.registerShader(evt);
            }catch(java.io.IOException err){
                throw new RuntimeException("Failed to register surface floodlight shader", err);
            }
            try {
                AreaHighlightRenderTypes.onRegisterShaders(evt);
            } catch (java.io.IOException err) {
                throw new RuntimeException("Failed to register area highlight shader", err);
            }
            try {
                SoftParticleRenderTypes.onRegisterShaders(evt);
            } catch (java.io.IOException err) {
                throw new RuntimeException("Failed to register soft particle shaders", err);
            }
        });
    }
}
