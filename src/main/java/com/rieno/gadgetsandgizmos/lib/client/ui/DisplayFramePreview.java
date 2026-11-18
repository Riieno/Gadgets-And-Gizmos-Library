package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.display.SurfaceFit;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Draw target media through the same fitted terminal and live camera renderers as other hosts
public final class DisplayFramePreview{
    private static final Map<ResourceLocation, Renderer> RENDERERS = new HashMap<>();
    private DisplayFramePreview(){}

    // Register host-owned formats without adding their implementation classes to the library
    public static void register(ResourceLocation format, Renderer renderer){
        Objects.requireNonNull(format); Objects.requireNonNull(renderer);
        if(RENDERERS.putIfAbsent(format, renderer) != null) throw new IllegalArgumentException("Duplicate display preview format: " + format);
    }

    public static boolean supports(CompoundTag frame){
        return renderer(frame) != null || switch(frame.getString("Format")){ case "terminal", "camera", "text" -> true; default -> false; };
    }

    // Keep a fitted surface's pointer coordinates aligned with its rendered preview
    public static GraphPortInspector.GuiPreview preview(CompoundTag frame){
        return new GraphPortInspector.GuiPreview(){
            @Override public void draw(GuiGraphics graphics, Font font, GraphPortInspector.Rect bounds){
                DisplayFramePreview.draw(graphics, font, frame, bounds.x(), bounds.y(), bounds.width(), bounds.height());
            }
            @Override public double[] point(GraphPortInspector.Rect bounds, double x, double y){
                int width = Math.max(1, frame.getInt("Width")), height = Math.max(1, frame.getInt("Height"));
                if("terminal".equals(frame.getString("Format"))){ width *= 6; height *= 9; }
                return SurfaceFit.fit(width, height, bounds.width(), bounds.height(), true).point(x - bounds.x(), y - bounds.y());
            }
        };
    }

    public static void draw(GuiGraphics graphics, Font font, CompoundTag frame, int x, int y, int width, int height){
        var renderer = renderer(frame);
        if(renderer != null){ renderer.draw(graphics, font, frame, x, y, width, height); return; }
        if("camera".equals(frame.getString("Format"))){
            ViewFeedRenderer.draw(graphics, font, ViewReference.fromTag(frame.getCompound("ViewSource")), x, y, width, height); return;
        }
        var terminal = ScriptTerminal.fromDisplayFrame(frame);
        if(terminal != null){ TerminalPreview.render(graphics, terminal, x, y, width, height); return; }
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        int row = y + 4;
        for(var line : frame.getList("Lines", Tag.TAG_STRING)){
            if(row + font.lineHeight > y + height) break;
            graphics.drawString(font, font.plainSubstrByWidth(line.getAsString(), Math.max(1, width - 8)), x + 4, row, 0xFFFFFFFF, false);
            row += font.lineHeight + 2;
        }
    }

    private static Renderer renderer(CompoundTag frame){
        var format = ResourceLocation.tryParse(frame.getString("Format"));
        return format == null ? null : RENDERERS.get(format);
    }

    @FunctionalInterface public interface Renderer{
        void draw(GuiGraphics graphics, Font font, CompoundTag frame, int x, int y, int width, int height);
    }
}
