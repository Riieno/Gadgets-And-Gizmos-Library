package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

// Draw and select up to four independently captured sources on one GUI surface
public final class ViewFeedGrid{
    private final List<Area> areas = new ArrayList<>();
    private final long started = net.minecraft.Util.getMillis();

    // Fit one view or a four-view grid inside the host's content bounds
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       List<Entry> entries, String selected){
        areas.clear();
        int columns = entries.size() > 1 ? 2 : 1;
        int rows = entries.size() > 2 ? 2 : 1;
        int cellWidth = Math.max(1, (width - (columns - 1) * 4) / columns);
        int cellHeight = Math.max(1, (height - (rows - 1) * 4) / rows);
        for(int idx = 0; idx < Math.min(4, entries.size()); idx++){
            Entry entry = entries.get(idx);
            int left = x + idx % columns * (cellWidth + 4);
            int top = y + idx / columns * (cellHeight + 4);
            graphics.fill(left, top, left + cellWidth, top + cellHeight, 0xFF101A20);
            graphics.pose().pushPose();
            graphics.pose().translate(left, top + 15, 0);
            boolean ready = entry.available() && ViewSceneRenderer.drawFitted(entry.source(), cellWidth,
                    Math.max(1, cellHeight - 15), 0, graphics.pose(), graphics.bufferSource());
            graphics.pose().popPose();
            graphics.flush();
            if(!ready) graphics.drawCenteredString(font, entry.available() ? "Waiting for camera" : "Camera unavailable",
                    left + cellWidth / 2, top + cellHeight / 2, 0xFFB2C7D0);
            if(ready){
                var viewport = ViewSceneRenderer.fittedViewport(entry.source(), cellWidth, Math.max(1, cellHeight - 15));
                int vx = left + Math.round(viewport.x()), vy = top + 15 + Math.round(viewport.y());
                int vw = Math.round(viewport.width()), vh = Math.round(viewport.height());
                graphics.enableScissor(vx, vy, vx + vw, vy + vh);
                var source = entry.source().resolve(net.minecraft.client.Minecraft.getInstance().level);
                var pose = source == null ? null : source.viewPose(0);
                ViewfinderOverlay.render(RemoteViewHud.canvas(graphics), font, vx, vy, vw, vh,
                        com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig.FRAME.get(),
                        com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig.GRID.get(),
                        pose == null ? 70 : pose.fov(), net.minecraft.Util.getMillis() - started);
                graphics.disableScissor();
            }
            graphics.fill(left, top, left + cellWidth, top + 15,
                    entry.key().equals(selected) ? 0xFF3D766D : 0xFF253B49);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), Math.max(1, cellWidth - 8)),
                    left + 4, top + 3, 0xFFF0F6FA, false);
            areas.add(new Area(entry.key(), left, top, cellWidth, cellHeight));
        }
    }
    // Identify the clicked feed without imposing the host's selection protocol
    public String selection(double x, double y){
        for(Area area : areas){
            if(x >= area.x && x < area.x + area.width && y >= area.y && y < area.y + area.height) return area.key;
        }
        return "";
    }
    public record Entry(String key, String name, ViewReference source, boolean available){}
    private record Area(String key, int x, int y, int width, int height){}
}
