package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.client.input.ClientActionKey;
import com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig;
import com.rieno.gadgetsandgizmos.lib.view.ControlledViewSource;
import com.rieno.gadgetsandgizmos.lib.view.RaycastViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewSource;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

// Show current assignments and sensor feedback only while the local player controls a lens
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class RemoteViewHud{
    private RemoteViewHud(){}

    // Leave the bottom edge available for lens shortcuts while normal item controls are suspended
    @SubscribeEvent public static void hideItemControls(RenderGuiLayerEvent.Pre evt){
        if(!RemoteViewClient.isActive() || Minecraft.getInstance().screen != null) return;
        var name = evt.getName();
        if(name.equals(VanillaGuiLayers.HOTBAR) || name.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL) || name.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) evt.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post evt){
        var mc = Minecraft.getInstance();
        if(!RemoteViewClient.isActive() || mc.options.hideGui || mc.player == null || mc.screen != null) return;
        var source = RemoteViewClient.source();
        if(source == null) return;
        var graphics = evt.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        try{ renderOverlay(graphics, source, evt.getPartialTick().getGameTimeDeltaPartialTick(false)); }
        finally{ graphics.pose().popPose(); }
    }
    private static void renderOverlay(GuiGraphics graphics, ViewSource source, float partialTick){
        var mc = Minecraft.getInstance();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        Component hints = Component.translatable("gadgetsngizmos.camera.hint.exit", RemoteViewKeys.exitLabel());
        if(source instanceof ControlledViewSource) hints = append(hints, hint("flashlight", RemoteViewKeys.FLASHLIGHT));
        if(source instanceof RaycastViewSource){
            hints = append(hints, hint("raycast", RemoteViewKeys.RAYCAST));
            hints = append(hints, hint("interact", RemoteViewKeys.INTERACT));
        }
        hints = append(append(hints, hint("frame", RemoteViewKeys.FRAME)), hint("grid", RemoteViewKeys.GRID));
        var lines = mc.font.split(hints, Math.max(1, width - 32));
        int hintHeight = lines.size() * (mc.font.lineHeight + 2);
        int top = height - hintHeight - 7;
        graphics.fill(8, top - 4, width - 8, height - 3, 0xB3152028);
        for(int idx = 0; idx < lines.size(); idx++) graphics.drawString(mc.font, lines.get(idx),
                (width - mc.font.width(lines.get(idx))) / 2, top + idx * (mc.font.lineHeight + 2), 0xFFE9F2EB, true);
        var pose = RemoteViewClient.pose(partialTick);
        ViewfinderOverlay.render(canvas(graphics), mc.font, 0, 0, width, height,
                ViewfinderConfig.FRAME.get(), ViewfinderConfig.GRID.get(), pose == null ? 70 : pose.fov(),
                Util.getMillis() - RemoteViewClient.startedAt(), height - top + 3);
        var result = RemoteViewClient.rayResult();
        if(result != null && Util.getMillis() - RemoteViewClient.rayResultTime() < 4000){
            Component text = result.hit() ? Component.translatable("gadgetsngizmos.camera.ray.hit",
                    String.format(Locale.ROOT, "%.2f", result.distance()),
                    String.format(Locale.ROOT, "%.1f, %.1f, %.1f", result.position().x, result.position().y, result.position().z))
                    : Component.translatable("gadgetsngizmos.camera.ray.miss", String.format(Locale.ROOT, "%.2f", result.distance()));
            int textWidth = mc.font.width(text), y = top - mc.font.lineHeight - 12;
            graphics.fill((width - textWidth) / 2 - 5, y - 3, (width + textWidth) / 2 + 5, y + mc.font.lineHeight + 3, 0xC0152028);
            graphics.drawString(mc.font, text, (width - textWidth) / 2, y, result.hit() ? 0xFFACE8B9 : 0xFFE6D1A2, true);
        }
    }
    private static Component hint(String name, ClientActionKey key){
        return Component.translatable("gadgetsngizmos.camera.hint." + name, RemoteViewKeys.label(key));
    }
    private static Component append(Component text, Component hint){ return text.copy().append("   |   ").append(hint); }

    public static ViewControlPanel.Canvas canvas(GuiGraphics graphics){
        return new ViewControlPanel.Canvas(){
            @Override public void fill(int left, int top, int right, int bottom, int color){ graphics.fill(left, top, right, bottom, color); }
            @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow){ graphics.drawString(font, text, x, y, color, shadow); }
        };
    }
}
