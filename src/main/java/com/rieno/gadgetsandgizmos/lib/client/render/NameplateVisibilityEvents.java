package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

// Apply explicit nameplate suppression even when a player renderer normally shows every name
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class NameplateVisibilityEvents{
    private NameplateVisibilityEvents(){}

    // Preserve ordinary nameplate rules unless the rendered entity explicitly hides its label
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderNameplate(RenderNameTagEvent event){
        if(event.getEntity() instanceof NameplateVisibility visibility && !visibility.isNameplateVisible()){
            event.setCanRender(TriState.FALSE);
        }
    }
}
