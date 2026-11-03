package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletLauncherThemes;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletLauncherThemes.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Refresh available client textures and combine them with server theme names
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class TabletLauncherResources{
    private static volatile Map<ResourceLocation, Theme> available = Map.of();

    private TabletLauncherResources(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rebuild the catalog instead of retaining themes removed by a pack reload
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent evt){
        evt.registerReloadListener((ResourceManagerReloadListener)TabletLauncherResources::reload);
    }

    // Publish one complete catalog after a resource reload
    public static void reload(ResourceManager resources){
        Map<ResourceLocation, Theme> next = new HashMap<>();
        for(Theme theme : TabletLauncherThemes.discover(resources)){
            if(resources.getResource(theme.texture()).isPresent()) next.put(theme.texture(), theme);
        }
        available = Map.copyOf(next);
    }

    // Show only entries whose texture is available on this client
    public static List<Theme> themes(ListTag serverThemes){
        Map<ResourceLocation, Theme> res = new HashMap<>(available);
        for(int idx = 0; idx < serverThemes.size(); idx++){
            Theme theme = Theme.fromTag(serverThemes.getCompound(idx));
            if(theme != null && res.containsKey(theme.texture())) res.put(theme.texture(), theme);
        }
        return res.values().stream().sorted(Comparator.comparing(theme -> theme.texture().toString())).toList();
    }

    // Fall back when an old wallpaper or a removed pack is selected
    public static ResourceLocation resolve(String saved, ResourceLocation fallback){
        ResourceLocation texture = ResourceLocation.tryParse(saved);
        return texture != null && available.containsKey(texture) ? texture : fallback;
    }

    // Scale the whole painted canvas, including packs with higher resolution artwork
    public static void render(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height){
        graphics.blit(texture, x, y, width, height, 0, 0, 320, 180, 320, 180);
    }
}
