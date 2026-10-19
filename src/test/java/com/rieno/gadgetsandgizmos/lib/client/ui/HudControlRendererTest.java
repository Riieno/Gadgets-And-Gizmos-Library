package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog.ControlStyle;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog.ProgressStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudControlRendererTest{
    @Test void inactiveMaterialFilledControlsDoNotShowTheAccentFill(){
        var bounds = new HudControlRenderer.Rect(0, 0, 140, 28);
        var palette = HudControlRenderer.Palette.DEFAULT;
        HudControlRenderer.Canvas canvas = (a,b,c,d,e,f,g,h,color,layer) ->
                assertNotEquals(palette.accent(), color, "Inactive control shows active fill");
        HudControlRenderer.button(canvas, bounds, palette, ControlStyle.MATERIAL_FILLED, false, false);
        HudControlRenderer.toggle(canvas, bounds, palette, ControlStyle.MATERIAL_FILLED, false, false, false);
        HudControlRenderer.toggle(canvas, bounds, palette, ControlStyle.MATERIAL_FILLED, false, false, true);
    }

    @Test void allControlStylesFitSmallAndNormalWidgets(){
        for(double[] size : new double[][]{{1, 1}, {6, 4}, {20, 80}, {140, 28}}){
            var bounds = new HudControlRenderer.Rect(10, 20, size[0], size[1]);
            HudControlRenderer.Canvas canvas = bounded(bounds);
            for(ControlStyle style : ControlStyle.values()){
                HudControlRenderer.button(canvas, bounds, HudControlRenderer.Palette.DEFAULT, style, true, true);
                HudControlRenderer.toggle(canvas, bounds, HudControlRenderer.Palette.DEFAULT, style, true, true, false);
                HudControlRenderer.toggle(canvas, bounds, HudControlRenderer.Palette.DEFAULT, style, true, true, true);
                for(double amount : new double[]{0, 0.45, 1})
                    HudControlRenderer.slider(canvas, bounds, HudControlRenderer.Palette.DEFAULT, style, amount, true);
            }
        }
    }

    @Test void allProgressStylesFitTheirCanvasAtBothEndpointsAndForInvalidValues(){
        for(double[] size : new double[][]{{1, 1}, {160, 24}, {90, 90}, {140, 70}}){
            var bounds = new HudControlRenderer.Rect(10, 20, size[0], size[1]);
            for(ProgressStyle style : ProgressStyle.values()){
                for(double amount : new double[]{-1, 0, 0.67, 1, 2, Double.NaN}){
                    HudControlRenderer.Rect label = HudControlRenderer.progress(bounded(bounds), bounds,
                            HudControlRenderer.Palette.DEFAULT, style, amount);
                    assertTrue(label.width() >= 0 && label.height() >= 0);
                    assertTrue(label.x() >= bounds.x() && label.right() <= bounds.right() + 0.001);
                    assertTrue(label.y() >= bounds.y() && label.bottom() <= bounds.bottom() + 0.001);
                }
            }
        }
    }

    @Test void theSixButtonLooksHaveDistinctGeometry(){
        var bounds = new HudControlRenderer.Rect(0, 0, 140, 28);
        HashSet<List<String>> designs = new HashSet<>();
        for(ControlStyle style : ControlStyle.values()){
            List<String> shapes = new ArrayList<>();
            HudControlRenderer.Canvas canvas = (a,b,c,d,e,f,g,h,color,layer) ->
                    shapes.add(List.of(a,b,c,d,e,f,g,h,color,layer).toString());
            HudControlRenderer.button(canvas, bounds, HudControlRenderer.Palette.DEFAULT, style, false, false);
            assertFalse(shapes.isEmpty());
            designs.add(shapes);
        }
        assertEquals(6, designs.size());
    }

    @Test void aSolidBarFillsExactlyTheConfiguredFraction(){
        List<Double> fillEdges = new ArrayList<>();
        var palette = HudControlRenderer.Palette.DEFAULT;
        HudControlRenderer.Canvas canvas = (a,b,c,d,e,f,g,h,color,layer) -> {
            if(color == palette.accent()) fillEdges.add(Math.max(e, g));
        };
        HudControlRenderer.progress(canvas, new HudControlRenderer.Rect(0, 0, 200, 20), palette, ProgressStyle.SOLID, 0);
        assertTrue(fillEdges.isEmpty());
        HudControlRenderer.progress(canvas, new HudControlRenderer.Rect(0, 0, 200, 20), palette, ProgressStyle.SOLID, 0.35);
        assertEquals(List.of(70.0), fillEdges);
    }

    private static HudControlRenderer.Canvas bounded(HudControlRenderer.Rect bounds){
        return (a,b,c,d,e,f,g,h,color,layer) -> {
            for(double x : new double[]{a,c,e,g}){
                assertTrue(Double.isFinite(x));
                assertTrue(x >= bounds.x() - 0.001 && x <= bounds.right() + 0.001, "x outside widget: " + x);
            }
            for(double y : new double[]{b,d,f,h}){
                assertTrue(Double.isFinite(y));
                assertTrue(y >= bounds.y() - 0.001 && y <= bounds.bottom() + 0.001, "y outside widget: " + y);
            }
        };
    }
}
