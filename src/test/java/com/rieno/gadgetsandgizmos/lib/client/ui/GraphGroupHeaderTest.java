package com.rieno.gadgetsandgizmos.lib.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphGroupHeaderTest{
    @Test void longTitleDoesNotOverlapTheColorButton(){
        GraphGroupHeader.Layout bounds = GraphGroupHeader.layout(100, 50, 320, 1, 900);
        assertTrue(bounds.titleX() + bounds.titleWidth() < bounds.buttonX());
        assertFalse(bounds.titleContains(bounds.buttonX() + 2, bounds.buttonY() + 2));
        assertTrue(bounds.colorContains(bounds.buttonX() + 2, bounds.buttonY() + 2));
    }

    @Test void shortTitleLeavesHeaderSpaceForDragging(){
        GraphGroupHeader.Layout bounds = GraphGroupHeader.layout(100, 50, 320, 1, 42);
        assertTrue(bounds.titleContains(110, 55));
        assertFalse(bounds.titleContains(200, 55));
        assertFalse(bounds.colorContains(200, 55));
        assertFalse(bounds.titleContains(110, 70));
    }

    @Test void hitBoundsFollowGraphZoom(){
        for(double zoom : new double[]{0.05, 0.5, 1, 1.75}){
            GraphGroupHeader.Layout bounds = GraphGroupHeader.layout(100, 50, (int)(320 * zoom), zoom, 42);
            assertTrue(bounds.colorContains(bounds.buttonX(), bounds.buttonY()));
            assertFalse(bounds.colorContains(bounds.buttonX() + bounds.buttonSize(), bounds.buttonY()));
            assertTrue(bounds.titleContains(bounds.titleX(), bounds.headerY()));
            assertFalse(bounds.titleContains(bounds.titleX(), bounds.headerY() + bounds.headerHeight()));
        }
    }
}
