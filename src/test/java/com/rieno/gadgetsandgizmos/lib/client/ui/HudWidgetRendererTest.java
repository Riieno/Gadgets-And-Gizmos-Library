package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify authored label, texture and control layout before either host projects its canvas
class HudWidgetRendererTest{
    @Test void textFieldsUseTheirArtworkInsetsAndDoNotShowOversizedLabels(){
        var canvas = new Capture();
        HudWidgetRenderer.draw(canvas, font(), "text_input", style(9, "regular"), 100, 28, "Text", .5, false, false);
        assertEquals(9, canvas.patches.size());
        assertEquals(new Patch(0, 0, 18, 5), canvas.patches.getFirst());
        assertEquals(new Patch(80, 24, 20, 4), canvas.patches.getLast());
        assertEquals(1, canvas.labels.size());
        assertEquals(4, canvas.labels.getFirst().x);
        assertEquals(10, canvas.labels.getFirst().y);
        var small = new Capture();
        HudWidgetRenderer.draw(small, font(), "text_input", style(20, "bold"), 100, 16, "Text", .5, false, false);
        assertTrue(small.labels.isEmpty());
    }

    @Test void sliderPaintingDoesNotIntroduceAnExtraLabelRow(){
        var canvas = new Capture();
        HudWidgetRenderer.draw(canvas, font(), "slider", style(9, "regular"), 90, 18, "Label", .5, false, false);
        assertTrue(canvas.labels.isEmpty());
        assertFalse(canvas.quads.isEmpty());
        for(double[] quad : canvas.quads) for(int idx = 1; idx < 8; idx += 2){
            assertTrue(quad[idx] >= 0 && quad[idx] <= 18);
        }
    }

    @Test void plainTextRetainsItsFontSizeAndCenteredLabelsFitUsingStyledWidths(){
        var plain = new Capture();
        HudWidgetRenderer.draw(plain, font(), "text", style(18, "bold"), 40, 2, "ABCDEF", 0, false, false);
        var label = plain.labels.getFirst();
        assertEquals(2, label.scale);
        assertEquals("AB", label.text.getString());
        assertTrue(label.text.getStyle().isBold());
        assertEquals(0, label.y);
        var button = new Capture();
        HudWidgetRenderer.draw(button, font(), "button", style(18, "bold"), 40, 12, "ABCDEF", 0, false, false);
        label = button.labels.getFirst();
        assertEquals(6, label.x, 1e-5);
        assertEquals(3, label.y, 1e-5);
        assertEquals(2.0 / 3, label.scale, 1e-5);
    }

    @Test void wrappingPreservesLineBreaksAndFitsTheElementHeight(){
        var canvas = new Capture();
        HudWidgetRenderer.wrappedText(canvas, font(), style(9, "italic"), new HudControlRenderer.Rect(0, 0, 60, 27), "Home\nStorage and Hangar");
        assertEquals(3, canvas.labels.size());
        assertEquals("Home", canvas.labels.getFirst().text.getString());
        for(var label : canvas.labels){
            assertTrue(label.text.getStyle().isItalic());
            assertTrue(font().width(label.text) <= 60);
            assertTrue(label.y + 9 <= 27);
        }
    }

    @Test void dropdownPresetsKeepLabelsClearOfTheirArrow(){
        for(var preset : HudStyleCatalog.DropdownStyle.values()){
            var canvas = new Capture();
            HudWidgetRenderer.dropdown(canvas, font(), style(9, "bold"), 100, 24, "Home - coordinates", preset, false, true);
            assertFalse(canvas.quads.isEmpty());
            assertEquals(1, canvas.labels.size());
            var label = canvas.labels.getFirst();
            assertTrue(label.x >= 0 && label.x + font().width(label.text) * label.scale < 85);
            assertTrue(label.text.getStyle().isBold());
        }
    }

    @Test void dropdownArrowsKeepTheirFrontFaceWhenOpened(){
        var closed = new Capture(); var open = new Capture();
        var bounds = new HudControlRenderer.Rect(0, 0, 100, 24);
        HudDropdownRenderer.control(closed, bounds, HudControlRenderer.Palette.DEFAULT, HudStyleCatalog.DropdownStyle.FLAT, false, false);
        HudDropdownRenderer.control(open, bounds, HudControlRenderer.Palette.DEFAULT, HudStyleCatalog.DropdownStyle.FLAT, true, false);
        double[] a = closed.quads.getLast(), b = open.quads.getLast();
        double crossA = (a[2] - a[0]) * (a[5] - a[1]) - (a[3] - a[1]) * (a[4] - a[0]);
        double crossB = (b[2] - b[0]) * (b[5] - b[1]) - (b[3] - b[1]) * (b[4] - b[0]);
        assertTrue(crossA * crossB > 0);
    }

    @Test void smallNineSlicesRetainNonnegativePatchesWithoutAtlasGutters(){
        List<Patch> patches = new ArrayList<>();
        frame().paint((region, x, y, width, height) -> {
            assertTrue(width >= 0 && height >= 0);
            assertTrue(x >= 0 && y >= 0 && x + width <= 15 && y + height <= 8);
            patches.add(new Patch(x, y, width, height));
        }, 0, 0, 15, 8, 1);
        assertEquals(120, patches.stream().mapToInt(p -> p.width * p.height).sum());
    }

    private static HudWidgetRenderer.Style style(int size, String fontStyle){
        return new HudWidgetRenderer.Style(HudControlRenderer.Palette.DEFAULT, size, fontStyle, -1,
                HudStyleCatalog.ControlStyle.MATERIAL_FILLED, HudStyleCatalog.ProgressStyle.SOLID, false, frame());
    }

    private static GuiNineSlice frame(){
        return new GuiNineSlice(part(0, 0, 18, 5), part(20, 0, 17, 5), part(39, 0, 20, 5),
                part(0, 6, 18, 8), part(20, 6, 17, 8), part(39, 6, 20, 8),
                part(0, 15, 18, 4), part(20, 15, 17, 4), part(39, 15, 20, 4));
    }

    private static GuiTextureRegion part(int u, int v, int width, int height){
        return new GuiTextureRegion(ResourceLocation.parse("test:text_input"), 59, 19, u, v, width, height);
    }

    private static Font font(){
        var splitter = new StringSplitter((codePoint, style) -> style.isBold() ? 7 : 6);
        return new Font(id -> null, false){
            @Override public StringSplitter getSplitter(){ return splitter; }
            @Override public int width(FormattedText text){ return (int)Math.ceil(splitter.stringWidth(text)); }
            @Override public FormattedText substrByWidth(FormattedText text, int width){
                return splitter.headByWidth(text, width, Style.EMPTY);
            }
        };
    }

    private record Label(Component text, double x, double y, float scale){}
    private record Patch(int x, int y, int width, int height){}
    private static final class Capture implements HudWidgetRenderer.Canvas{
        private final List<double[]> quads = new ArrayList<>();
        private final List<Label> labels = new ArrayList<>();
        private final List<Patch> patches = new ArrayList<>();
        @Override public void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                                   double x4, double y4, int color, int layer){ quads.add(new double[]{x1, y1, x2, y2, x3, y3, x4, y4}); }
        @Override public void draw(GuiTextureRegion region, int x, int y, int width, int height){ patches.add(new Patch(x, y, width, height)); }
        @Override public void text(Component text, double x, double y, float scale, int color){ labels.add(new Label(text, x, y, scale)); }
    }
}
