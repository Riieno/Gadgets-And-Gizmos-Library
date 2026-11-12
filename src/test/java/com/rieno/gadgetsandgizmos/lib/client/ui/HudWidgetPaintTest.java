package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.color.ColorRamp;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Verify complete gradient coverage and preserve geometry on every canvas implementation
class HudWidgetPaintTest{
    @Test void boxPaintRetainsEndpointsIntermediateStopsAndOpacityWithoutGaps(){
        var canvas = new Capture();
        var ramp = new ColorRamp(List.of(0x40FF0000, 0x8000FF00, 0xFF0000FF));
        var style = style();
        HudWidgetPaint.draw(canvas, font(), 120, style, Map.of("Color", ramp), true,
                (target, sampled) -> HudWidgetRenderer.draw(target, font(), "box", sampled, 120, 20, "", 0, false, false));
        assertEquals(2400, canvas.area, .00001);
        assertTrue(canvas.colors.contains(0x40FF0000));
        assertTrue(canvas.colors.contains(0xFF0000FF));
        assertTrue(canvas.colors.stream().anyMatch(color -> (color >>> 8 & 255) > 245));
        assertEquals(0, canvas.minX);
        assertEquals(120, canvas.maxX);
    }

    @Test void clippingRetainsSlopingEdgesAndFrontFaceWinding(){
        var canvas = new Capture();
        HudWidgetPaint.draw(canvas, font(), 100, style(), Map.of("AccentColor", new ColorRamp(List.of(-1, 0xFF000000))), false,
                (target, sampled) -> HudWidgetPaint.quad(target, 0, 0, 100, 0, 50, 20, 0, 0, target.paint("AccentColor", sampled.palette().accent()), 1));
        assertEquals(1000, canvas.area, .00001);
        assertTrue(canvas.signedAreas.stream().allMatch(area -> area >= -1e-9));
    }

    @Test void gradientLabelsRetainWhitespaceUnicodeAndFontStyleAndDrawOnce(){
        var canvas = new Capture();
        HudWidgetPaint.draw(canvas, font(), 120, style(), Map.of("Color", new ColorRamp(List.of(0xFFFF0000, 0xFF0000FF))), false,
                (target, sampled) -> target.text(Component.literal(" A😀 B ").withStyle(Style.EMPTY.withBold(true)), 0, 0, 1, -1));
        assertEquals(1, canvas.labels.size());
        var text = canvas.labels.getFirst();
        assertEquals(" A😀 B ", text.getString());
        var colors = new ArrayList<Integer>();
        text.visit((style, part) -> {
            if(!part.isEmpty()){
                assertTrue(style.isBold());
                colors.add(style.getColor().getValue());
            }
            return Optional.empty();
        }, Style.EMPTY);
        assertTrue(colors.stream().distinct().count() > 1);
    }

    private static HudWidgetRenderer.Style style(){
        var palette = new HudControlRenderer.Palette(-1, -1, -1, -1, -1, 0, 0, 1);
        return new HudWidgetRenderer.Style(palette, 9, "regular", -1, HudStyleCatalog.ControlStyle.MATERIAL_FILLED,
                HudStyleCatalog.ProgressStyle.SOLID, false, null);
    }

    private static Font font(){
        var splitter = new StringSplitter((codePoint, style) -> style.isBold() ? 7 : 6);
        return new Font(id -> null, false){
            @Override public StringSplitter getSplitter(){ return splitter; }
            @Override public int width(FormattedText text){ return (int)Math.ceil(splitter.stringWidth(text)); }
        };
    }

    private static final class Capture implements HudWidgetRenderer.Canvas{
        double area, minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        final List<Integer> colors = new ArrayList<>();
        final List<Double> signedAreas = new ArrayList<>();
        final List<Component> labels = new ArrayList<>();

        @Override public void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                                   double x4, double y4, int color, int layer){
            double signed = (x1 * y2 - x2 * y1 + x2 * y3 - x3 * y2 + x3 * y4 - x4 * y3 + x4 * y1 - x1 * y4) / 2;
            signedAreas.add(signed); area += Math.abs(signed); colors.add(color);
            minX = Math.min(minX, Math.min(Math.min(x1, x2), Math.min(x3, x4)));
            maxX = Math.max(maxX, Math.max(Math.max(x1, x2), Math.max(x3, x4)));
        }
        @Override public void draw(GuiTextureRegion region, int x, int y, int width, int height){}
        @Override public void text(Component text, double x, double y, float scale, int color){ labels.add(text); }
    }
}
