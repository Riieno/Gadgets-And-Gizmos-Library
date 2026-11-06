package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.HashMap;
import java.util.Map;

// Allow code-rendered app icons alongside the existing texture icons
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class TabletAppIcons{
    private static final Map<ResourceLocation, Renderer> RENDERERS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> TEXTURES = new HashMap<>();
    private TabletAppIcons(){}

    public static void register(ResourceLocation appId, Renderer renderer){
        if(RENDERERS.putIfAbsent(appId, renderer) != null) throw new IllegalStateException(LocalizedText.get("gadgetsngizmos.text.app_icon_already_registered") + appId);
    }

    public static void render(TabletAppDefinition app, GuiGraphics graphics, int x, int y, int size){
        if(size <= 0) return;
        TabletAppIconStyle style = TabletAppIconStyles.style(app);
        Renderer renderer = RENDERERS.get(app.id());
        if(!style.forceFallback() && renderer != null){
            renderer.render(graphics, x, y, size);
        }else if(!style.forceFallback() && TEXTURES.computeIfAbsent(app.icon(),
                icon -> Minecraft.getInstance().getResourceManager().getResource(icon).isPresent())){
            graphics.blit(app.icon(), x, y, size, size, 0, 0, 64, 64, 64, 64);
        }else{
            graphics.fill(x, y, x + size, y + size, 0xFF000000 | style.color());
            var item = BuiltInRegistries.ITEM.getOptional(style.item()).orElse(Items.PAPER);
            if(item == Items.AIR) item = Items.PAPER;
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + size / 8F, y + size / 8F, 0);
                graphics.pose().scale(size * .75F / 16, size * .75F / 16, 1);
                graphics.renderItem(new ItemStack(item), 0, 0);
            }finally{ graphics.pose().popPose(); }
        }
    }

    // Resource packs may add or remove app artwork between reloads
    @SubscribeEvent public static void registerReloadListeners(RegisterClientReloadListenersEvent evt){
        evt.registerReloadListener((ResourceManagerReloadListener) resources -> TEXTURES.clear());
    }

    @FunctionalInterface
    public interface Renderer{ void render(GuiGraphics graphics, int x, int y, int size); }
}
