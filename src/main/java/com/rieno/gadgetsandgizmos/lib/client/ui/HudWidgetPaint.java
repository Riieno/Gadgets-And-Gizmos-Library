package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.color.ColorRamp;
import com.rieno.gadgetsandgizmos.lib.color.ColorPaint;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

// Apply element gradients to the same control geometry on GUI and world canvases
public final class HudWidgetPaint{
    private HudWidgetPaint(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample every authored stop while retaining each control style's shading and borders
    public static void draw(HudWidgetRenderer.Canvas canvas, Font font, int width,
                            HudWidgetRenderer.Style style, Map<String, ColorRamp> paints, boolean box,
                            BiConsumer<HudWidgetRenderer.Canvas, HudWidgetRenderer.Style> painter){
        if(paints.isEmpty() || width <= 0){ painter.accept(canvas, style); return; }
        painter.accept(new HudWidgetRenderer.Canvas(){
            @Override public ColorPaint paint(String property, int fallback){
                ColorRamp ramp = paints.get(box && "BackgroundColor".equals(property) ? "Color" : property);
                return ramp == null ? canvas.paint(property, fallback) : ColorPaint.gradient(ramp, width);
            }
            @Override public void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                                       double x4, double y4, int color, int layer){
                canvas.quad(x1, y1, x2, y2, x3, y3, x4, y4, color, layer);
            }
            @Override public void draw(GuiTextureRegion region, int x, int y, int w, int h){ canvas.draw(region, x, y, w, h); }
            @Override public void text(Component text, double x, double y, float scale, int color){
                canvas.text(paints.containsKey("Color")
                        ? HudWidgetPaint.text(font, text, paints.get("Color"), x, scale, width) : text, x, y, scale, color);
            }
        }, style);
    }

    // Subdivide only painted geometry while preserving solid quads and winding
    public static void quad(HudControlRenderer.Canvas canvas, double x1, double y1, double x2, double y2,
                            double x3, double y3, double x4, double y4, ColorPaint paint, int layer){
        if(paint instanceof ColorPaint.Solid solid){
            canvas.quad(x1, y1, x2, y2, x3, y3, x4, y4, solid.color(), layer);
            return;
        }
        double min = Math.min(Math.min(x1, x2), Math.min(x3, x4)), max = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        int strips = Math.min(100, Math.max(1, (int)Math.ceil(max - min)));
        for(int idx = 0; idx < strips; idx++){
            double left = min + (max - min) * idx / strips, right = min + (max - min) * (idx + 1) / strips;
            int color = paint.sample((left + right) / 2);
            if((color >>> 24) == 0) continue;
            if(x1 == x2 && x3 == x4 && y1 == y4 && y2 == y3){
                if(x1 <= x3) canvas.quad(left, y1, left, y2, right, y3, right, y4, color, layer);
                else canvas.quad(right, y1, right, y2, left, y3, left, y4, color, layer);
            }else clipped(canvas, left, right, new double[]{x1, y1, x2, y2, x3, y3, x4, y4}, color, layer);
        }
    }

    // Preserve font styles and Unicode characters while coloring glyphs across the element
    public static Component text(Font font, Component text, ColorRamp ramp, double x, float scale, double width){
        var res = Component.empty();
        double[] advance = {x};
        text.visit((style, part) -> {
            part.codePoints().forEach(codePoint -> {
                String glyph = Character.toString(codePoint);
                double glyphWidth = font.getSplitter().stringWidth(Component.literal(glyph).setStyle(style)) * scale;
                res.append(Component.literal(glyph).setStyle(style.withColor(ramp.sample((advance[0] + glyphWidth / 2) / Math.max(1, width)) & 0xFFFFFF)));
                advance[0] += glyphWidth;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return res;
    }

    // Clip polygons geometrically so rotated controls and world surfaces need no screen scissor
    private static void clipped(HudControlRenderer.Canvas canvas, double left, double right, double[] quad, int color, int layer){
        double min = Math.min(Math.min(quad[0], quad[2]), Math.min(quad[4], quad[6]));
        double max = Math.max(Math.max(quad[0], quad[2]), Math.max(quad[4], quad[6]));
        if(max <= left || min >= right) return;
        if(min >= left && max <= right){
            canvas.quad(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], quad[6], quad[7], color, layer);
            return;
        }
        List<Point> polygon = new ArrayList<>();
        for(int idx = 0; idx < 8; idx += 2) polygon.add(new Point(quad[idx], quad[idx + 1]));
        polygon = clip(clip(polygon, left, true), right, false);
        for(int idx = 1; idx + 1 < polygon.size(); idx++){
            var a = polygon.getFirst(); var b = polygon.get(idx); var c = polygon.get(idx + 1);
            canvas.quad(a.x(), a.y(), b.x(), b.y(), c.x(), c.y(), c.x(), c.y(), color, layer);
        }
    }

    private static List<Point> clip(List<Point> polygon, double edge, boolean lower){
        List<Point> res = new ArrayList<>();
        if(polygon.isEmpty()) return res;
        Point prev = polygon.getLast();
        boolean prevInside = lower ? prev.x() >= edge : prev.x() <= edge;
        for(Point point : polygon){
            boolean inside = lower ? point.x() >= edge : point.x() <= edge;
            if(inside != prevInside){
                double amount = (edge - prev.x()) / (point.x() - prev.x());
                res.add(new Point(edge, prev.y() + (point.y() - prev.y()) * amount));
            }
            if(inside) res.add(point);
            prev = point;
            prevInside = inside;
        }
        return res;
    }

    private record Point(double x, double y){}
}
